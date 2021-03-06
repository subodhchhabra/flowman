/*
 * Copyright 2018 Kaya Kupferschmidt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimajix.flowman.spec.schema

import java.io.IOException
import java.io.StringWriter
import java.net.URL
import java.nio.charset.Charset

import scala.collection.JavaConversions._

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import io.swagger.models.ComposedModel
import io.swagger.models.Model
import io.swagger.models.ModelImpl
import io.swagger.models.Swagger
import io.swagger.models.properties.ArrayProperty
import io.swagger.models.properties.BaseIntegerProperty
import io.swagger.models.properties.BinaryProperty
import io.swagger.models.properties.BooleanProperty
import io.swagger.models.properties.ByteArrayProperty
import io.swagger.models.properties.DateProperty
import io.swagger.models.properties.DateTimeProperty
import io.swagger.models.properties.DecimalProperty
import io.swagger.models.properties.DoubleProperty
import io.swagger.models.properties.FloatProperty
import io.swagger.models.properties.IntegerProperty
import io.swagger.models.properties.LongProperty
import io.swagger.models.properties.MapProperty
import io.swagger.models.properties.ObjectProperty
import io.swagger.models.properties.Property
import io.swagger.models.properties.StringProperty
import io.swagger.models.properties.UUIDProperty
import io.swagger.parser.util.DeserializationUtils
import io.swagger.parser.util.SwaggerDeserializer
import io.swagger.util.Json
import org.apache.commons.io.IOUtils

import com.dimajix.flowman.execution.Context
import com.dimajix.flowman.fs.File
import com.dimajix.flowman.types.ArrayType
import com.dimajix.flowman.types.BinaryType
import com.dimajix.flowman.types.BooleanType
import com.dimajix.flowman.types.DateType
import com.dimajix.flowman.types.DoubleType
import com.dimajix.flowman.types.Field
import com.dimajix.flowman.types.FieldType
import com.dimajix.flowman.types.FloatType
import com.dimajix.flowman.types.IntegerType
import com.dimajix.flowman.types.LongType
import com.dimajix.flowman.types.MapType
import com.dimajix.flowman.types.StringType
import com.dimajix.flowman.types.StructType
import com.dimajix.flowman.types.TimestampType


/**
  * Schema implementation for reading Swagger / OpenAPI schemas. This implementation will preserve the ordering of
  * fields.
  */
class SwaggerSchema extends ExternalSchema {
    @JsonProperty(value="entity", required=false) private var _entity: String = _

    def entity(implicit context: Context) : String = context.evaluate(_entity)

    /**
      * Returns the description of the schema
      * @param context
      * @return
      */
    override def description(implicit context: Context): String = {
        loadSwaggerSchema.getInfo.getDescription
    }

    /**
      * Returns the list of all fields of the schema
      * @param context
      * @return
      */
    override def fields(implicit context: Context): Seq[Field] = {
        val swagger = loadSwaggerSchema
        val model = Option(entity).filter(_.nonEmpty).map(e => swagger.getDefinitions()(e)).getOrElse(swagger.getDefinitions().values().head)

        if (!model.isInstanceOf[ModelImpl] && !model.isInstanceOf[ComposedModel])
            throw new IllegalArgumentException("Root type in Swagger must be a simple model or composed model")

        fromSwaggerModel(model)
    }

    private def loadSwaggerSchema(implicit context: Context) : Swagger = {
        val string = loadSchema(context)
        convertToSwagger(string)
    }

    @throws[IOException]
    private def convertToSwagger(data: String): Swagger = {
        val rootNode = if (data.trim.startsWith("{")) {
            val mapper = Json.mapper
            mapper.readTree(data)
        }
        else {
            DeserializationUtils.readYamlTree(data)
        }

        // Fix nested "allOf" nodes, which have to be in "definitions->[Entity]->[Definition]"
        val possiblyUnparsableNodes = rootNode.path("definitions").elements().flatMap(_.elements())
        possiblyUnparsableNodes.foreach(replaceAllOf)

        val result = new SwaggerDeserializer().deserialize(rootNode)
        val convertValue = result.getSwagger
        convertValue
    }

    /**
      * This helper method transforms the Json tree such that "allOf" inline elements will be replaced by an
      * adequate object definition, because Swagger will not parse inline allOf elements correctly
      * @param jsonNode
      */
    private def replaceAllOf(jsonNode: JsonNode) : Unit = {
        jsonNode match {
            case obj:ObjectNode =>
                if (obj.get("allOf") != null) {
                    val children = obj.get("allOf").elements().toSeq
                    val required = children.flatMap(_.get("required").elements().toSeq)
                    val properties = children.flatMap(_.get("properties").fields())
                    val desc = children.flatMap(c => Option(c.get("description"))).headOption
                    obj.without("allOf")
                    obj.set("type", TextNode.valueOf("object"))
                    obj.withArray("required").addAll(required)
                    properties.foreach(x => obj.`with`("properties").set(x.getKey, x.getValue))
                    desc.foreach(d => obj.set("description", d))
                }
            case _:JsonNode =>
        }
        jsonNode.elements().foreach(replaceAllOf)
    }

    private def fromSwaggerModel(model:Model) : Seq[Field] = {
        model match {
            case composed:ComposedModel => composed.getAllOf.flatMap(fromSwaggerModel)
            //case array:ArrayModel => Seq(fromSwaggerProperty(array.getItems))
            case _ => fromSwaggerObject(model.getProperties.toSeq, "").fields
        }
    }

    private def fromSwaggerObject(properties:Seq[(String,Property)], prefix:String) : StructType = {
        StructType(properties.map(np => fromSwaggerProperty(np._1, np._2, prefix)))
    }

    private def fromSwaggerProperty(name:String, property:Property, prefix:String) : Field = {
        Field(name, fromSwaggerType(property, prefix + name), !property.getRequired, property.getDescription)
    }

    private def fromSwaggerType(property:Property, fqName:String) : FieldType = {
        property match {
            case array:ArrayProperty => ArrayType(fromSwaggerType(array.getItems, fqName + ".items"))
            case _:BinaryProperty => BinaryType
            case _:BooleanProperty => BooleanType
            case _:ByteArrayProperty => BinaryType
            case _:DateProperty => DateType
            case _:DateTimeProperty => TimestampType
            case _:FloatProperty => FloatType
            case _:DoubleProperty => DoubleType
            case _:DecimalProperty => DoubleType
            case _:IntegerProperty => IntegerType
            case _:LongProperty => LongType
            case _:BaseIntegerProperty => IntegerType
            case _:MapProperty => MapType(StringType, StringType)
            case obj:ObjectProperty => fromSwaggerObject(obj.getProperties.toSeq, fqName + ".")
            case _:StringProperty => StringType
            case _:UUIDProperty => StringType
            case _ => throw new UnsupportedOperationException(s"Swagger type $property of field $fqName not supported")
        }
    }

}
