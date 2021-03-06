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

package com.dimajix.flowman.util

import java.io.StringWriter

import scala.math.Ordering

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.slf4j.LoggerFactory


/**
  * Helper class for collecting files from a file system, which also support pattern substitution
  *
  * @param hadoopConf
  */
class FileCollector(hadoopConf:Configuration) {
    private val logger = LoggerFactory.getLogger(classOf[FileCollector])

    private implicit val fileStatusOrder = new Ordering[FileStatus] {
        def compare(x: FileStatus, y: FileStatus): Int = x.getPath compareTo y.getPath
    }

    private var _pattern:String = ""
    private var _path:Path = _

    private lazy val templateEngine = Templating.newEngine()
    private lazy val templateContext = Templating.newContext()

    /**
      * Constructs a new FileCollector instance using the Hadoop configuration from the specified SparkSession
      *
      * @param spark
      * @return
      */
    def this(spark:SparkSession) = {
        this(spark.sparkContext.hadoopConfiguration)
    }

    /**
      * Sets the pattern which will be used for generating directory and/or file names from partition information
      *
      * @param pattern
      * @return
      */
    def pattern(pattern:String) : FileCollector = {
        this._pattern = pattern
        this
    }

    /**
      * Sets the base directory which is used for retrieving the file system. The base location must not contain
      * any pattern variable
      *
      * @param path
      * @return
      */
    def path(path:Path) : FileCollector = {
        this._path = path
        this
    }

    def resolve(partition:Map[String,Any]) : Path = {
        if (_pattern != null && _pattern.nonEmpty) {
            val context = templateContext
            partition.foreach(kv => context.put(kv._1, kv._2))
            val output = new StringWriter()
            templateEngine.evaluate(context, output, "FileCollector", _pattern)
            new Path(_path, output.getBuffer.toString)
        }
        else {
            _path
        }
    }

    /**
      * Collects files from the given partitions
      *
      * @param partitions
      * @return
      */
    def collect(partitions:Map[String,Iterable[Any]]) : Seq[Path] = {
        logger.info(s"Collecting files in location ${_path} with pattern '${_pattern}'")
        if (_path == null || _path.toString.isEmpty)
            throw new IllegalArgumentException("path needs to be defined for collecting partitioned files")
        if (_pattern == null)
            throw new IllegalArgumentException("pattern needs to be defined for collecting partitioned files")

        val fs = _path.getFileSystem(hadoopConf)
        PartitionUtils.flatMap(partitions, p => {
            val curPath:Path = resolve(p)
            val partitions = p.map { case(k,v) => k + "=" + v }.mkString(",")

            logger.info(s"Collecting files for partition $partitions in location $curPath")
            collectPath(fs, curPath)
        }).toSeq
    }

    /**
      * Collects files from the configured directory. Does not perform partition resolution
      *
      * @return
      */
    def collect() : Seq[Path] = {
        logger.info(s"Collecting files in location ${_path} with pattern '${_pattern}'")
        if (_path == null || _path.toString.isEmpty)
            throw new IllegalArgumentException("path needs to be defined for collecting files")

        val fs = _path.getFileSystem(hadoopConf)
        val curPath:Path = if (_pattern != null && _pattern.nonEmpty) new Path(_path, _pattern) else _path
        collectPath(fs, curPath)
    }

    private def collectPath(fs:FileSystem, path:Path) = {
        if (fs.isDirectory(path)) {
            // If path is a directory, simply list all files
            fs.listStatus(path).sorted.map(_.getPath).toSeq
        }
        else {
            // Otherwise assume a file pattern and try to glob all files
            val files = fs.globStatus(path)
            if (files != null)
                files.sorted.map(_.getPath).toSeq
            else
                Seq()
        }
    }
}
