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

package com.dimajix.flowman.spec.flow

import org.apache.spark.sql.Row
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.dimajix.flowman.LocalSparkSession
import com.dimajix.flowman.execution.Session
import com.dimajix.flowman.spec.Module
import com.dimajix.flowman.spec.MappingIdentifier


class ProjectMappingTest extends FlatSpec with Matchers with LocalSparkSession {
    "The ProjectMapping" should "work" in {
        val df = spark.createDataFrame(Seq(
            ("col1", 12),
            ("col2", 23)
        ))

        val session = Session.builder().withSparkSession(spark).build()
        val executor = session.executor
        implicit val context = executor.context

        val mapping = new ProjectMapping
        mapping._input = "myview"
        mapping._columns = Map("_2" -> "int")

        mapping.input should be (MappingIdentifier("myview"))
        mapping.columns should be (Seq("_2" -> "int"))
        mapping.dependencies should be (Array(MappingIdentifier("myview")))

        val result = mapping.execute(executor, Map(MappingIdentifier("myview") -> df)).orderBy("_2").collect()
        result.size should be (2)
        result(0) should be (Row(12))
        result(1) should be (Row(23))
    }

    "An appropriate Dataflow" should "be readable from YML" in {
        val spec =
            """
              |mappings:
              |  t1:
              |    kind: project
              |    input: t0
              |    columns:
              |      _2: string
              |      _1: string
            """.stripMargin

        val project = Module.read.string(spec).toProject("project")
        val session = Session.builder().withSparkSession(spark).build()
        val executor = session.executor
        implicit val context = executor.context

        project.mappings.size should be (1)
        project.mappings.contains("t0") should be (false)
        project.mappings.contains("t1") should be (true)

        val df = spark.createDataFrame(Seq(
            ("col1", 12),
            ("col2", 23)
        ))

        val mapping = project.mappings("t1")
        mapping.execute(executor, Map(MappingIdentifier("t0") -> df)).orderBy("_1", "_2")
    }

}
