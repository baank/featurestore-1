import diamond.io.{CSVSink, CSVSource}
import diamond.transformation.TransformationContext
import diamond.transformation.row.{AppendColumnRowTransformation, RowTransformation}
import diamond.transformation.sql.NamedSQLTransformation
import diamond.transformation.table.RowTransformationPipeline
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by markmo on 12/12/2015.
  */
class WorkflowSpec extends UnitSpec {

  val conf = new SparkConf().setAppName("Test").setMaster("local[4]")
  val sc = new SparkContext(conf)
  val sqlContext = new SQLContext(sc)

  val path = getClass.getResource("events_sample.csv").getPath

  val inputSchema = StructType(
    StructField("entityIdType", StringType) ::
      StructField("entityId", StringType) ::
      StructField("attribute", StringType) ::
      StructField("ts", StringType) ::
      StructField("value", StringType, nullable = true) ::
      StructField("properties", StringType, nullable = true) ::
      StructField("processTime", StringType) :: Nil
  )

  val rawDF =
    sqlContext.read
      .format("com.databricks.spark.csv")
      .option("header", "false")
      .schema(inputSchema)
      .load(path)

  rawDF.registerTempTable("events")

  /*
    * Need to be careful with these transforms as they can drop columns
    * appended by AppendColumnTransformations if done out of sequence.
    *
  object HelloTransform extends RowTransformation {

    // A name is required for reporting and debugging purposes
    val name = "Hello"

    /**
      * This is where the transformation logic goes.
      *
      * using the fieldLocator helper
      *
      * @param row a spark.sql.Row
      * @param ctx a TransformationContext
      * @return the new spark.sql.Row
      */
    def apply(row: Row, ctx: TransformationContext) = {
      val f = fieldLocator(row, ctx)
      Row(
        f("entityIdType"),
        f("entityId"),
        s"Hello ${f("attribute")}",
        f("ts"),
        f("value"),
        f("properties"),
        f("processTime")
      )
    }

  }*/


  // define aliases

  val Transform = RowTransformation

  val AppendColumn = AppendColumnRowTransformation

  // import helper functions such as fieldLocator
  import RowTransformation._

  val HelloTransform = Transform(
    name = "Hello"
  ) { (row, ctx) => {
    val f = fieldLocator(row, ctx)
    Row(
      f("entityIdType"),
      f("entityId"),
      s"Hello ${f("attribute")}",
      f("ts"),
      f("value"),
      f("properties"),
      f("processTime")
    )
  }}

  /*
  object WorldTransform extends RowTransformation {

    val name = "World"

    // using the built-in Row methods
    def apply(row: Row, ctx: TransformationContext) =
      Row(
        row.getString(0),
        row.getString(1),
        "World " + row.getString(2),
        row.getString(3),
        row.getString(4),
        row.getString(5),
        row.getString(6)
      )

  }*/

  val WorldTransform = Transform(
    name = "World"
  ) { (row, ctx) =>
    Row(
      row.getString(0),
      row.getString(1),
      "World " + row.getString(2),
      row.getString(3),
      row.getString(4),
      row.getString(5),
      row.getString(6)
    )
  }

  /*
    * This type of transform appends a new column to the end
    * so the developer doesn't need to construct a full Row.
    *
    * Implement "append" instead of "apply".
    *
  object FiftyTransform extends AppendColumnRowTransformation {

    val name = "Fifty"

    val columnName = "column_7"

    val dataType = IntegerType

    val nullable = true

    def append(row: Row, ctx: TransformationContext) = 50

  }*/

  val FiftyTransform = AppendColumn(
    name = "Fifty",
    columnName = "column_7",
    dataType = IntegerType
  ) { (row, ctx) =>
    50
  }

  /*
  object AddFiveTransform extends AppendColumnRowTransformation {

    val name = "AddFive"

    val columnName = "column_8"

    val dataType = IntegerType

    val nullable = true

    def append(row: Row, ctx: TransformationContext) = row.getInt(7) + 5

    addDependencies(FiftyTransform)

  }*/

  val AddFiveTransform = AppendColumn(
    name = "AddFive",
    columnName = "column_8",
    dataType = IntegerType
  ) { (row, ctx) =>
    row.getInt(7) + 5
  } addDependencies FiftyTransform


  // Tests

  "A CSV file" should "load into a DataFrame" in {
    rawDF.take(3).length should be(3)
  }

  "A Pipeline" should "apply a transformation to the RDD" in {

    // A Pipeline will scan through the entire dataset, performing
    // one or more transformations per row, in the right sequence.
    // This work will be parallelized thanks to Spark.
    val pipeline = new RowTransformationPipeline("test")

    // dependencies can be defined in the Transform object or outside
    // Must be a DAG i.e. no cycles

    HelloTransform.addDependencies(WorldTransform)

    FiftyTransform.addDependencies(HelloTransform)

    // order doesn't matter - will respect dependencies
    pipeline.addTransformations(AddFiveTransform, HelloTransform, WorldTransform, FiftyTransform)

    // create a new context object that can pass state between transformations
    val ctx = new TransformationContext

    // set the schema into the context
    ctx(RowTransformation.SCHEMA_KEY, rawDF.schema)

    // execute the pipeline
    val results = pipeline(rawDF, ctx)

    // print some results

//    println {
//      results.printSchema()
//    }

//    results.take(3).foreach(println)

//    println {
//      pipeline.printDAG()
//    }

    // our tests
    val first = results.take(1)(0)

    first.getString(2).startsWith("Hello World") should be(true)

    first.getInt(7) should be(50)

    first.getInt(8) should be(55)
  }

  it should "read from a Source and write to a Sink" in {
    val source = CSVSource(sqlContext)

    val sink = CSVSink(sqlContext)

    val ctx = new TransformationContext
    ctx("in_path", path)
    ctx("schema", inputSchema)
    ctx("out_path", "/tmp/workflow_spec_out_csv")

    val pipeline = new RowTransformationPipeline("test")
    pipeline.addTransformations(AddFiveTransform, HelloTransform, WorldTransform, FiftyTransform)

    val results = pipeline.run(source, sink, ctx)

    val first = results.take(1)(0)
    first.getString(2).startsWith("Hello World") should be(true)
    first.getInt(7) should be(50)
    first.getInt(8) should be(55)
  }

  "A NamedSQLTransformation" should "read and execute a SQL statement from a properties file given a symbolic name" in {
    val transform = new NamedSQLTransformation("/sql.properties", "query1")

    val results = transform(sqlContext)

    results.take(1).foreach(println)

    results.collect().length should be > 10
  }


  it should "read and execute a SQL statement from an XML configuration file given a symbolic name" in {
    val transform = new NamedSQLTransformation("/sql.xml", "query1")

    val results = transform(sqlContext)

    results.take(1).foreach(println)

    results.collect().length should be > 10
  }

  /* WIP - NOT READY
  "DSL notation" should "execute a pipeline" in {
    import diamond.transformation._

    // create a new context object that can pass state between transformations
    val ctx = new TransformationContext

    // set the schema into the context
    ctx(RowTransformation.SCHEMA_KEY, rawDF.schema)

    given(rawDF, ctx) {
      rows {
        transform(name = "Hello") {
          Row(
            f("entityIdType"),
            f("entityId"),
            s"Hello ${f("attribute")}",
            f("ts"),
            f("value"),
            f("properties"),
            f("processTime")
          )
        } /> {
          transform(name = "World") {
            Row(
              row.getString(0),
              row.getString(1),
              "World " + row.getString(2),
              row.getString(3),
              row.getString(4),
              row.getString(5),
              row.getString(6)
            )
          }
        },
        append(
          name = "AddFive",
          columnName = "column_8",
          dataType = IntegerType
        ) {
          55
        } /> {
          append(
            name = "Fifty",
            columnName = "column_7",
            dataType = IntegerType
          ) {
            50
          }
        }
      }
    }

  }*/

}
