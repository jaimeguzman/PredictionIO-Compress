package cl.jguzman.piocompressapp

import grizzled.slf4j.Logger


//CONTROLLLER
import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params

//STORAGE
import io.prediction.data.storage.Storage
import io.prediction.data.storage.Event

import io.prediction.data.store.PEventStore



//SPARK
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint



/*
* Parametros para la lectura de datos de origen.
*
* */
case class DataSourceParams(
    appId: Int

) extends Params







class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
                    EmptyEvaluationInfo,
                    Query,
                    EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override def readTraining(sc: SparkContext): TrainingData = {

    println("\n::::::::RATSLABS:::Recuperando Información desde el servidor de eventos.")
    val eventosDB = Storage.getPEvents()






    val eventosRDD : RDD[Event] =  eventosDB.find(
                            appId = dsp.appId,
                            entityType = Some("user")
                          )(sc)

    val dataEnRDD = eventosRDD.map{
                            ev  => ev.event match {
                              case "view" =>{

                                val entId = ev.entityId
                                val page  = ev.properties.get[String]("page")
                                val pos   = ev.properties.get[Int]("pos")

                                System.out.print( " entityID\t"+entId+"\tpage:\t "+page+"\tpos\t "+pos+"\t "    )

                              }
                              case _ => throw new Exception(s"Evento no esperado, ${ev} ha sido leido")

                            }

                  }

    println("::::::::RATSLABS:::dataEnRDD "+dataEnRDD.getClass )
    dataEnRDD.foreach( f => println( f ))





    val puntosEtiquetadosPT: RDD[LabeledPoint] = eventosDB.aggregateProperties(
      appId = dsp.appId,
      entityType = "user",
      required = Some(List("view", "page", "pos")))(sc) .map { case (entityId, properties) =>


      println("\n::::::::RATSLABS:::"+ properties.get[String]("page") )
      println("\n::::::::RATSLABS:::"+ properties.get[Int]("pos") )

      try {
        LabeledPoint(entityId,
        Vectors.d
          Vectors.dense(Array(
            //properties.get[String]("page"),
            properties.get[Int]("pos")
          ))
        )
      //LabeledPoint( 6.9 , Vectors.dense(1.0, 0.0, 3.0) )

      } catch {
        case e: Exception => {
          logger.error(s"FALLO AL INTENTAR OBTEBER LAS properties ${properties} DE" +
            s" ${entityId}. Exception: ${e}.")
          throw e
        }
      }


    }   //end map //

    print("\n\n\n \n ")
    println("\n::::::::RATSLABS::: labeledPOINTS loaded count()"+ puntosEtiquetadosPT.count())







    print("\n\n\n \n\n ")

    new TrainingData(puntosEtiquetadosPT)
  }
}




class TrainingData(
     val labeledPoints: RDD[LabeledPoint]
) extends Serializable
