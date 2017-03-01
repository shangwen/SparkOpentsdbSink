package org.apache.spark.metrics.sink

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.codahale.metrics.MetricRegistry
import com.github.sps.metrics.OpenTsdbReporter
import com.github.sps.metrics.opentsdb.OpenTsdb
import org.apache.spark.SecurityManager
import org.apache.spark.metrics.MetricsSystem
import org.apache.spark.internal.Logging

/**
  * This file is taken (with some changes) from pull request to Spark
  * https://github.com/apache/spark/pull/10187/commits/df43bb46d055611167c1253a497ff36f23248733
  * Issue: https://issues.apache.org/jira/browse/SPARK-12194
  *
  * Properties template:
  * # org.apache.spark.metrics.sink.OpenTsdbSink
  * #   Name:       Default:            Description:
  * #   host        NONE                Hostname of OpenTsdb server
  * #   port        NONE                Port of OpenTsdb server
  * #   period      10                  Poll period
  * #   unit        seconds             Units of poll period
  * #   prefix      EMPTY STRING        Prefix to prepend to metric name
  * #   tagName1                        Name of one required tag for OpenTsdb
  * #   tagValue1                       Value of one required tag for OpenTsdb
  */
private[spark] class OpenTsdbSink(val property: Properties, val registry: MetricRegistry,
                                  securityMgr: SecurityManager) extends Sink with Logging{

  logInfo("OpenTsdbSink initialized")

  val OPENTSDB_DEFAULT_PERIOD = 10
  val OPENTSDB_DEFAULT_UNIT = "SECONDS"
  val OPENTSDB_DEFAULT_PREFIX = ""

  val OPENTSDB_KEY_HOST = "host"
  val OPENTSDB_KEY_PORT = "port"
  val OPENTSDB_KEY_PERIOD = "period"
  val OPENTSDB_KEY_UNIT = "unit"
  val OPENTSDB_KEY_PREFIX = "prefix"

  val OPENTSDB_KEY_TAG_NAME_1 = "tagName1"
  val OPENTSDB_KEY_TAG_VALUE_1 = "tagValue1"
  val OPENTSDB_KEY_TAG_NAME_2 = "tagName2"
  val OPENTSDB_KEY_TAG_VALUE_2 = "tagValue2"
  val OPENTSDB_KEY_TAG_NAME_3 = "tagName3"
  val OPENTSDB_KEY_TAG_VALUE_3 = "tagValue3"
  val OPENTSDB_KEY_TAG_NAME_4 = "tagName4"
  val OPENTSDB_KEY_TAG_VALUE_4 = "tagValue4"
  val OPENTSDB_KEY_TAG_NAME_5 = "tagName5"
  val OPENTSDB_KEY_TAG_VALUE_5 = "tagValue5"
  val OPENTSDB_KEY_TAG_NAME_6 = "tagName6"
  val OPENTSDB_KEY_TAG_VALUE_6 = "tagValue6"
  val OPENTSDB_KEY_TAG_NAME_7 = "tagName7"
  val OPENTSDB_KEY_TAG_VALUE_7 = "tagValue7"
  val OPENTSDB_KEY_TAG_NAME_8 = "tagName8"
  val OPENTSDB_KEY_TAG_VALUE_8 = "tagValue8"

  def propertyToOption(prop: String): Option[String] = Option(property.getProperty(prop))

  if (propertyToOption(OPENTSDB_KEY_HOST).isEmpty) {
    throw new Exception(s"OpenTSDB sink requires '$OPENTSDB_KEY_HOST' property.")
  }

  if (propertyToOption(OPENTSDB_KEY_PORT).isEmpty) {
    throw new Exception(s"OpenTSDB sink requires '$OPENTSDB_KEY_PORT' property.")
  }

  val host = propertyToOption(OPENTSDB_KEY_HOST).get
  val port = propertyToOption(OPENTSDB_KEY_PORT).get.toInt

  val pollPeriod = propertyToOption(OPENTSDB_KEY_PERIOD) match {
    case Some(s) => s.toInt
    case None => OPENTSDB_DEFAULT_PERIOD
  }

  val pollUnit: TimeUnit = propertyToOption(OPENTSDB_KEY_UNIT) match {
    case Some(s) => TimeUnit.valueOf(s.toUpperCase())
    case None => TimeUnit.valueOf(OPENTSDB_DEFAULT_UNIT)
  }

  MetricsSystem.checkMinimalPollingPeriod(pollUnit, pollPeriod)

  val prefix = propertyToOption(OPENTSDB_KEY_PREFIX).getOrElse(OPENTSDB_DEFAULT_PREFIX)

  private val tags = new java.util.HashMap[String, String]()

  private def getTags(registry: MetricRegistry): java.util.Map[String, String] = {
    updateTagsForTag(OPENTSDB_KEY_TAG_NAME_1, OPENTSDB_KEY_TAG_VALUE_1)
    updateTagsForTag(OPENTSDB_KEY_TAG_NAME_2, OPENTSDB_KEY_TAG_VALUE_2)
    updateTagsForTag(OPENTSDB_KEY_TAG_NAME_3, OPENTSDB_KEY_TAG_VALUE_3)
    updateTagsForTag(OPENTSDB_KEY_TAG_NAME_4, OPENTSDB_KEY_TAG_VALUE_4)
    updateTagsForTag(OPENTSDB_KEY_TAG_NAME_5, OPENTSDB_KEY_TAG_VALUE_5)
    updateTagsForTag(OPENTSDB_KEY_TAG_NAME_6, OPENTSDB_KEY_TAG_VALUE_6)

    if (appId != null) {
      tags.put("appId", appId)
      tags.put("executorId", executorId)
    } else {
      updateTagsForTag(OPENTSDB_KEY_TAG_NAME_7, OPENTSDB_KEY_TAG_VALUE_7)
      updateTagsForTag(OPENTSDB_KEY_TAG_NAME_8, OPENTSDB_KEY_TAG_VALUE_8)

      if (tags.isEmpty) {
        tags.put("mandatoryTag", "mandatoryTagValue")
      }
    }

    tags
  }

  private def updateTagsForTag(tagName: String, tagValue: String): Unit = {
    propertyToOption(tagName) match {
      case Some(n) => propertyToOption(tagValue) match {
        case Some(v) => tags.put(n, v)
        case None =>
          throw new Exception(
            s"OpenTSDB sink requires '$tagValue' property when '$tagName' property is specified."
          )
      }
      case None =>
    }
  }

  // extract appId and executorId from first metric's name
  var appId: String = null
  var executorId: String = null

  if (!registry.getNames.isEmpty) {
    // search strings like app-20160526104713-0016.0.xxx or app-20160526104713-0016.driver.xxx
    val pattern = """(app-\d+-\d+)\.(\d+|driver)\..+""".r

    registry.getNames.first match {
      case pattern(app, executor) =>
        this.appId = app
        this.executorId = executor
      case _ =>
    }
  }

  val openTsdb = OpenTsdb.forService("http://" + host + ":" + port).create()

  lazy val reporter: OpenTsdbReporter = OpenTsdbReporter.forRegistry(registry)
    .prefixedWith(prefix)
    .removePrefix(Option(appId).map(_ + "." + executorId + ".").orNull)
    .withTags(getTags(registry))
    .build(openTsdb)

  override def start() {
    reporter.start(pollPeriod, pollUnit)
  }

  override def stop() {
    reporter.stop()
  }

  override def report() {
    reporter.report()
  }
}