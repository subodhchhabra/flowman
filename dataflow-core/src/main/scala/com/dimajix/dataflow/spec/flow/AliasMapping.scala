package com.dimajix.dataflow.spec.flow

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.spark.sql.DataFrame

import com.dimajix.dataflow.execution.Context
import com.dimajix.dataflow.execution.Executor
import com.dimajix.dataflow.execution.TableIdentifier


class AliasMapping extends BaseMapping {
    @JsonProperty(value = "input", required = true) private var _input:String = _

    def input(implicit context: Context) : TableIdentifier = context.evaluate(_input)

    /**
      * Executes this mapping by returning a DataFrame which corresponds to the specified input
      * @param executor
      * @param input
      * @return
      */
    override def execute(executor:Executor, input:Map[TableIdentifier,DataFrame]) : DataFrame = {
        implicit val context = executor.context
        input(this.input)
    }

    /**
      * Returns the dependencies of this mapping, which is exactly one input table
      *
      * @param context
      * @return
      */
    override def dependencies(implicit context: Context) : Array[TableIdentifier] = {
        Array(TableIdentifier(input))
    }
}
