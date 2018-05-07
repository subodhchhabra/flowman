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

package com.dimajix.flowman.execution

import java.util.NoSuchElementException

import scala.collection.mutable

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import com.dimajix.flowman.spec.Namespace
import com.dimajix.flowman.spec.Project
import com.dimajix.flowman.spec.TableIdentifier


private[execution] class RootExecutor(context:RootContext, sessionFactory:() => Option[SparkSession])
    extends AbstractExecutor(context, sessionFactory) {
    override protected val logger = LoggerFactory.getLogger(classOf[RootExecutor])

    private val _children = mutable.Map[String,ProjectExecutor]()
    private val _namespace = context.namespace

    /**
      * Returns the Namespace of the Executor
      *
      * @return
      */
    def namespace : Namespace = _namespace

     /**
      * Returns a fully qualified table as a DataFrame from a project belonging to the namespace of this executor
      *
      * @param name
      * @return
      */
    override def getTable(name: TableIdentifier): DataFrame = {
        if (name.project.isEmpty)
            throw new NoSuchElementException("Expected project name in table specifier")
        val child = _children.getOrElseUpdate(name.project.get, getProjectExecutor(name.project.get))
        child.getTable(TableIdentifier(name.name, None))
    }

    /**
      * Returns all tables belonging to the Root and child executors
      * @return
      */
    override def tables: Map[TableIdentifier, DataFrame] = {
        _children.values.map(_.tables).reduce(_ ++ _)
    }

    /**
      * Creates an instance of a table of a Dataflow, or retrieves it from cache
      *
      * @param identifier
      */
    override def instantiate(identifier: TableIdentifier): DataFrame = {
        if (identifier.project.isEmpty)
            throw new NoSuchElementException("Expected project name in table specifier")
        val child = getProjectExecutor(identifier.project.get)
        child.instantiate(TableIdentifier(identifier.name, None))
    }

    /**
      * Perform Spark related cleanup operations (like deregistering temp tables, clearing caches, ...)
      */
    override def cleanup(): Unit = {
        logger.info("Cleaning up root executor and all children")
        _children.values.foreach(_.cleanup())
    }

    def getProjectExecutor(project:Project) : ProjectExecutor = {
        _children.getOrElseUpdate(project.name, createProjectExecutor(project))
    }
    def getProjectExecutor(name:String) : ProjectExecutor = {
        _children.getOrElseUpdate(name, createProjectExecutor(name))
    }

    private def createProjectExecutor(project:Project) : ProjectExecutor = {
        val pcontext = context.getProjectContext(project)
        val executor = new ProjectExecutor(this, project, pcontext, createProjectSession(pcontext))
        _children.update(project.name, executor)
        executor
    }
    private def createProjectExecutor(project:String) : ProjectExecutor = {
        createProjectExecutor(context.loadProject(project))
    }
    private def createProjectSession(context: Context)() : Option[SparkSession] = {
        logger.info("Creating new Spark session for project")
        Option(spark) // .map(_.newSession())
    }
}
