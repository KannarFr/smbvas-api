package models

import java.io._
import java.net.URL
import java.nio.file._
import java.util.UUID
import javax.inject._

import scala.util.{Try, Success, Failure}

import play.api.libs.json._
import play.api.libs.Files._
import play.api.Logger

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.services.s3.model._

import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import java.net.URL;
import java.util.List;

import utils.Configuration

object cellar_module {
  implicit val urlWrites = new Writes[URL] {
    def writes(url: URL) = Json.obj("url" -> url.toString)
  }

  class CellarDTO @Inject()(conf: Configuration) {
    val endpointConfiguration = new EndpointConfiguration(conf.cellar.host, null)
    val credentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(conf.cellar.access_key, conf.cellar.secret_key))
    val s3Client = AmazonS3ClientBuilder.standard()
      .withCredentials(credentialsProvider)
      .withEndpointConfiguration(endpointConfiguration)
      .withPathStyleAccessEnabled(true)
      .build();
    val bucketName = conf.cellar.bucket_name

    def createResourceFile(uuid: UUID, file: TemporaryFile): Either[_, Unit] = {
      Try(s3Client.putObject(
          new PutObjectRequest(bucketName, uuid.toString, file)
            .withCannedAcl(CannedAccessControlList.PublicRead)
      )) match {
        case Failure(e) => {
          println(e.printStackTrace)
          e.printStackTrace
          Left(e)
        }
        case Success(_) => Right(())
      }
    }

    def getResourceUrl(uuid: UUID): Either[_, URL] = {
      Try(s3Client.getUrl(bucketName, uuid.toString)) match {
        case Failure(e) => Left(e)
        case Success(url) => Right(url)
      }
    }

    def deleteResourceFile(uuid: UUID): Either[_, Unit] = {
      Try(s3Client.deleteObject(bucketName, uuid.toString)) match {
        case Failure(e) => Left(e)
        case Success(_) => Right(())
      }
    }
  }
}
