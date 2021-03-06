---
layout: page
title: Flowman
permalink: /index.html
---

# What is Flowman

Flowman is a Spark based ETL program that simplifies the act of writing data transformations.
The main idea is that users write so called *specifications* in purely declarative YAML files
instead of writing Spark jobs in Scala or Python. The main advantage of this approach is that
many technical details of a correct and robust implementation are encapsulated and the user
can concentrate on the data transformations themselves.

In addition to writing and executing data transformations, Flowman can also be used for 
managing physical data models, i.e. Hive tables. Flowman can create such tables from a 
specification with the correct schema. This helps to keep all aspects (like transformations
and schema information) in a single place managed by a single program.

## Noteable Features

* Declarative syntax in YAML files
* Data model management (Create and Destroy Hive tables or file based storage)
* Flexible expression language
* Jobs for managing additional tasks (like copying files or uploading data via sftp)
* Powerful yet simple command line tool
* Extendable via Plugins


# Where to go from here

## Installation
* [Flowman Installation](installation.html): Installation Guide

## CLI Documentation

Flowman provides a command line utility (CLI) for running flows. Details are described in the
following sections:

* [Flowman CLI](cli/flowexec.html): Documentation of the Flowman CLI


## Specification Documentation

So called specifications describe the logical data flow, data sources and more. A full 
specification contains multiple entities like mappings, data models and jobs to be executed.
More detail on all these items is described in the following sections:

* [Specification Overview](spec/index.html): An introduction for writing new flows
* [Mappings](spec/mapping/index.html): Documentation of available data transformations
* [Relations](spec/relation/index.html): Documentation of available data sources and sinks
* [Outputs](spec/output/index.html): Documentation of available output operations
* [Schema](spec/schema/index.html): Documentation of available schema descriptions
* [Jobs & Tasks](spec/job/index.html): Documentation of creating jobs and executing tasks

