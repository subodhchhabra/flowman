package com.dimajix.flowman.tools.exec

import java.util.Locale

import org.kohsuke.args4j.CmdLineParser
import org.slf4j.LoggerFactory

import com.dimajix.flowman.execution.Session
import com.dimajix.flowman.spec.Namespace
import com.dimajix.flowman.spec.Project
import com.dimajix.flowman.util.splitSettings


object Driver {
    def main(args: Array[String]) : Unit = {
        // First create driver, so can already process arguments
        val options = new Arguments(args)
        val driver = new Driver(options)

        val result = driver.run()
        System.exit(if (result) 0 else 1)
    }
}


class Driver(options:Arguments) {
    private val logger = LoggerFactory.getLogger(classOf[Driver])

    /**
      * Main method for running this command
      * @return
      */
    def run() : Boolean = {
        // Adjust Spark loglevel
        if (options.sparkLogging != null) {
            val upperCased = options.sparkLogging.toUpperCase(Locale.ENGLISH)
            val l = org.apache.log4j.Level.toLevel(upperCased)
            org.apache.log4j.Logger.getLogger("org").setLevel(l)
            org.apache.log4j.Logger.getLogger("akka").setLevel(l)
        }

        if (options.help || options.command == null) {
            new CmdLineParser(if (options.command != null) options.command else options).printUsage(System.err)
            System.err.println
            System.exit(1)
        }

        val namespace:Namespace = null
        val sparkConfig = splitSettings(options.sparkConfig)
        val environment = splitSettings(options.environment)
        val session:Session = Session.builder
            .withNamespace(namespace)
            .withSparkName(options.sparkName)
            .withSparkConfig(sparkConfig.toMap)
            .withEnvironment(environment.toMap)
            .withProfiles(options.profiles)
            .build()

        val project:Project = Project.read.file(options.projectFile)

        options.command.execute(project, session)
    }
}