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

package com.dimajix.flowman.spec

class IdentifierFactory[T] {
    val empty = new Identifier[T]("", None)

    def apply(name:String) : Identifier[T] = parse(name)
    def apply(name:String, project:Option[String]) = new Identifier[T](name, project)
    def apply(name:String, project:String) = new Identifier[T](name, Some(project))
    def parse(fqName:String) : Identifier[T] = {
        if (fqName == null || fqName.isEmpty) {
            empty
        }
        else {
            val parts = fqName.split('/')
            new Identifier[T](parts.last, if (parts.size > 1) Some(parts.dropRight(1).mkString("/")) else None)
        }
    }
}
case class Identifier[T](name:String, project:Option[String]) {
    override def toString : String = {
        if (project.isEmpty)
            name
        else
            project.get + "/" + name
    }
}
