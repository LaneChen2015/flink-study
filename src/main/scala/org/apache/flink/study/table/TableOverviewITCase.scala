package org.apache.flink.study.table
import org.apache.flink.api.scala._
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.runtime.state.memory.MemoryStateBackend
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.table.api.{Table, TableEnvironment}
import org.apache.flink.table.api.scala._
import org.apache.flink.types.Row
import org.junit.rules.TemporaryFolder
import org.junit.{Rule, Test}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class  APIOverviewITCase {

// 客户表数据
  val customer_data = new mutable.MutableList[(String, String, String)]
  customer_data.+=(("c_001", "Kevin", "from JinLin"))
  customer_data.+=(("c_002", "Sunny", "from JinLin"))
  customer_data.+=(("c_003", "JinCheng", "from HeBei"))


  // 订单表数据
  val order_data = new mutable.MutableList[(String, String, String, String)]
  order_data.+=(("o_001", "c_002", "2018-11-05 10:01:01", "iphone"))
  order_data.+=(("o_002", "c_001", "2018-11-05 10:01:55", "ipad"))
  order_data.+=(("o_003", "c_001", "2018-11-05 10:03:44", "flink book"))

  // 商品销售表数据
  val item_data = Seq(
  Left((1510365660000L, (1510365660000L, 20, "ITEM001", "Electronic"))),
  Right((1510365660000L)),
  Left((1510365720000L, (1510365720000L, 50, "ITEM002", "Electronic"))),
  Right((1510365720000L)),
  Left((1510365780000L, (1510365780000L, 30, "ITEM003", "Electronic"))),
  Left((1510365780000L, (1510365780000L, 60, "ITEM004", "Electronic"))),
  Right((1510365780000L)),
  Left((1510365900000L, (1510365900000L, 40, "ITEM005", "Electronic"))),
  Right((1510365900000L)),
  Left((1510365960000L, (1510365960000L, 20, "ITEM006", "Electronic"))),
  Right((1510365960000L)),
  Left((1510366020000L, (1510366020000L, 70, "ITEM007", "Electronic"))),
  Right((1510366020000L)),
  Left((1510366080000L, (1510366080000L, 20, "ITEM008", "Clothes"))),
  Right((151036608000L)))

  // 页面访问表数据
  val pageAccess_data = Seq(
  Left((1510365660000L, (1510365660000L, "ShangHai", "U0010"))),
  Right((1510365660000L)),
  Left((1510365660000L, (1510365660000L, "BeiJing", "U1001"))),
  Right((1510365660000L)),
  Left((1510366200000L, (1510366200000L, "BeiJing", "U2032"))),
  Right((1510366200000L)),
  Left((1510366260000L, (1510366260000L, "BeiJing", "U1100"))),
  Right((1510366260000L)),
  Left((1510373400000L, (1510373400000L, "ShangHai", "U0011"))),
  Right((1510373400000L)))

  // 页面访问量表数据2
  val pageAccessCount_data = Seq(
  Left((1510365660000L, (1510365660000L, "ShangHai", 100))),
  Right((1510365660000L)),
  Left((1510365660000L, (1510365660000L, "BeiJing", 86))),
  Right((1510365660000L)),
  Left((1510365960000L, (1510365960000L, "BeiJing", 210))),
  Right((1510366200000L)),
  Left((1510366200000L, (1510366200000L, "BeiJing", 33))),
  Right((1510366200000L)),
  Left((1510373400000L, (1510373400000L, "ShangHai", 129))),
  Right((1510373400000L)))

  // 页面访问表数据3
  val pageAccessSession_data = Seq(
  Left((1510365660000L, (1510365660000L, "ShangHai", "U0011"))),
  Right((1510365660000L)),
  Left((1510365720000L, (1510365720000L, "ShangHai", "U0012"))),
  Right((1510365720000L)),
  Left((1510365720000L, (1510365720000L, "ShangHai", "U0013"))),
  Right((1510365720000L)),
  Left((1510365900000L, (1510365900000L, "ShangHai", "U0015"))),
  Right((1510365900000L)),
  Left((1510366200000L, (1510366200000L, "ShangHai", "U0011"))),
  Right((1510366200000L)),
  Left((1510366200000L, (1510366200000L, "BeiJing", "U2010"))),
  Right((1510366200000L)),
  Left((1510366260000L, (1510366260000L, "ShangHai", "U0011"))),
  Right((1510366260000L)),
  Left((1510373760000L, (1510373760000L, "ShangHai", "U0410"))),
  Right((1510373760000L)))

  val _tempFolder = new TemporaryFolder

  // Streaming 环境
  val env = StreamExecutionEnvironment.getExecutionEnvironment
  val tEnv = TableEnvironment.getTableEnvironment(env)
  env.setParallelism(1)
  env.setStateBackend(getStateBackend)

  def getProcTimeTables(): (Table, Table) = {
    // 将order_tab, customer_tab 注册到catalog
    val customer = env.fromCollection(customer_data).toTable(tEnv).as('c_id, 'c_name, 'c_desc)
    val order = env.fromCollection(order_data).toTable(tEnv).as('o_id, 'c_id, 'o_time, 'o_desc)
    (customer, order)
  }


  def getEventTimeTables():  (Table, Table, Table, Table) = {
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    // 将item_tab, pageAccess_tab 注册到catalog
    val item =
    env.addSource(new EventTimeSourceFunction[(Long, Int, String, String)](item_data))
    .toTable(tEnv, 'onSellTime, 'price, 'itemID, 'itemType, 'rowtime.rowtime)

    val pageAccess =
    env.addSource(new EventTimeSourceFunction[(Long, String, String)](pageAccess_data))
    .toTable(tEnv, 'accessTime, 'region, 'userId, 'rowtime.rowtime)

    val pageAccessCount =
    env.addSource(new EventTimeSourceFunction[(Long, String, Int)](pageAccessCount_data))
    .toTable(tEnv, 'accessTime, 'region, 'accessCount, 'rowtime.rowtime)

    val pageAccessSession =
    env.addSource(new EventTimeSourceFunction[(Long, String, String)](pageAccessSession_data))
    .toTable(tEnv, 'accessTime, 'region, 'userId, 'rowtime.rowtime)

    (item, pageAccess, pageAccessCount, pageAccessSession)
  }


  @Rule
  def tempFolder: TemporaryFolder = _tempFolder

  def getStateBackend: StateBackend = {
    new MemoryStateBackend()
  }

  def procTimePrint(result: Table): Unit = {
    val sink = new RetractingSink
    result.toRetractStream[Row].addSink(sink)
    env.execute()
  }

  def rowTimePrint(result: Table): Unit = {
    val sink = new RetractingSink
    result.toRetractStream[Row].addSink(sink)
    env.execute()
  }

  @Test
  def testProc(): Unit = {
    val (customer, order) = getProcTimeTables()

    // 测试查询逻辑
    val result= customer
      .leftOuterJoin(order.select('o_id, 'c_id as 'o_c_id, 'o_time, 'o_desc), 'c_id === 'o_c_id)
    procTimePrint(result)
  }

  @Test
  def testEvent(): Unit = {
    val (item, pageAccess, pageAccessCount, pageAccessSession) = getEventTimeTables()

    // 测试查询逻辑
    val result =item
      .window(Over partitionBy 'itemType orderBy 'rowtime preceding 2.rows following CURRENT_ROW as 'w)
      .select('itemID, 'itemType, 'onSellTime, 'price, 'price.max over 'w as 'maxPrice)
    procTimePrint(result)
  }

  }

  // 自定义Sink
  final class RetractingSink extends RichSinkFunction[(Boolean, Row)] {
    var retractedResults: ArrayBuffer[String] = null


    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      retractedResults = mutable.ArrayBuffer.empty[String]
    }

    override def invoke(v: (Boolean, Row)) {
      retractedResults.synchronized {
        val value = v._2.toString
        if (v._1) {
          retractedResults += value
        } else {
          val idx = retractedResults.indexOf(value)
          if (idx >= 0) {
            retractedResults.remove(idx)
          } else {
            throw new RuntimeException("Tried to retract a value that wasn't added first. " +
              "This is probably an incorrectly implemented test. " +
              "Try to set the parallelism of the sink to 1.")
          }
        }
      }

    }

    override def close(): Unit = {
      super.close()
      retractedResults.sorted.foreach(println(_))
    }
  }

  // Water mark 生成器
  class EventTimeSourceFunction[T](dataWithTimestampList: Seq[Either[(Long, T), Long]]) extends SourceFunction[T] {
    override def run(ctx: SourceContext[T]): Unit = {
      dataWithTimestampList.foreach {
        case Left(t) => ctx.collectWithTimestamp(t._2, t._1)
        case Right(w) => ctx.emitWatermark(new Watermark(w))
      }
    }

    override def cancel(): Unit = {}
}