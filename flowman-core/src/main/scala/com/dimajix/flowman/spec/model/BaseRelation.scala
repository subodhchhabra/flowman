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

package com.dimajix.flowman.spec.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.DataFrameReader
import org.apache.spark.sql.DataFrameWriter
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructType

import com.dimajix.flowman.execution.Context
import com.dimajix.flowman.execution.Executor
import com.dimajix.flowman.spec.schema.Schema


/**
  * Common base implementation for the Relation interface class. It contains a couple of common properties.
  */
abstract class BaseRelation extends Relation {
    @JsonProperty(value="schema", required=false) private var _schema: Schema = _
    @JsonProperty(value="description", required = false) private var _description: String = _
    @JsonProperty(value="options", required=false) private var _options:Map[String,String] = Map()

    /**
      * Returns a description for the relation
      * @param context
      * @return
      */
    override def description(implicit context: Context) : String = context.evaluate(_description)

    /**
      * Returns the schema of the relation
      * @param context
      * @return
      */
    override def schema(implicit context: Context) : Schema = _schema
    def options(implicit context: Context) : Map[String,String] = _options.mapValues(context.evaluate)

    /**
      * Creates a DataFrameReader which is already configured with options and the schema is also
      * already included
      * @param executor
      * @return
      */
    protected def reader(executor:Executor) : DataFrameReader = {
        implicit val context = executor.context
        val reader = executor.spark.read.options(options)
        if (_schema != null)
            reader.schema(inputSchema)

        reader
    }

    /**
      * Ceates a DataFrameWriter which is already configured with any options. Moreover
      * the desired schema of the relation is also applied to the DataFrame
      * @param executor
      * @param df
      * @return
      */
    protected def writer(executor: Executor, df:DataFrame) : DataFrameWriter[Row] = {
        implicit val context = executor.context
        val outputDf = applySchema(df)
        outputDf.write.options(options)
    }

    /**
      * Creates a Spark schema from the list of fields.
      * @param context
      * @return
      */
    protected def inputSchema(implicit context:Context) : StructType = {
        StructType(schema.fields.map(_.sparkField))
    }

    /**
      * Applies the specified schema (or maybe even transforms it)
      * @param df
      * @return
      */
    protected def applySchema(df:DataFrame)(implicit context:Context) : DataFrame = {
        if (_schema != null) {
            val outputColumns = schema.fields
                .map(_.sparkField)
                .map(field => df(field.name).cast(field.dataType).as(field.name, field.metadata))
            df.select(outputColumns: _*)
        }
        else {
            df
        }
    }
}
