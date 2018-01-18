package com.dimajix.dataflow.storage

import com.dimajix.dataflow.spec.Database
import com.dimajix.dataflow.spec.Profile
import com.dimajix.dataflow.spec.Project
import com.dimajix.dataflow.spec.model.Relation


abstract class Store {
    def loadProject(name:String) : Project
    def storeProject(project: Project) : Unit
    def removeProject(name:String) : Unit
    def listProjects() : Seq[String]

    def loadEnvironment() : Map[String,String]
    def addEnvironment(key:String, value:String) : Unit
    def removeEnvironment(key:String) : Unit
    
    def listProfiles() : Seq[String]
    def loadProfiles() : Map[String,Profile]
    def enableProfile(name:String) : Unit
    def disableProfile(name:String) : Unit
    def loadProfile(name:String) : Profile
    def storeProfile(name:String, Profile: Profile) : Unit
    def removeProfile(name:String) : Unit

    def listRelations() : Seq[String]
    def loadRelations() : Map[String,Profile]
    def loadRelation(name:String) : Relation
    def storeRelation(name:String, relation: Relation) : Unit
    def removeRelation(name:String) : Unit

    def listDatabases() : Seq[String]
    def loadDatabases() : Map[String,Profile]
    def loadDatabase(name:String) : Database
    def storeDatabase(name:String, database: Database) : Unit
    def removeDatabase(name:String) : Unit
}