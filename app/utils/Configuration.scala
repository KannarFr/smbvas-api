package utils

import javax.inject._
import play.api._
import play.api.libs.json.Json
import scala.collection.JavaConverters._

@Singleton
class Configuration @Inject()(
  c: play.api.Configuration
) {
  def getString(key: String, subKey: String): Option[String] = c.getOptional[String](key + "." + subKey)

  case class Cellar(
    host: String,
    access_key: String,
    secret_key: String,
    bucket_name: String
  )
  val cellar = ({
    val key = "s3"
    for {
      host <- getString(key, "host_base")
      access_key <- getString(key, "access_key")
      secret_key <- getString(key, "secret_key")
      bucket_name <- getString(key, "bucket_name")
    } yield {
      Cellar(host, access_key, secret_key, bucket_name)
    }
  }).get
}