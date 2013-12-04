package doobie

import java.sql._
import doobie.std._
import scalaz.effect._
import scalaz.effect.IO._
import scalaz._
import Scalaz._
import scala.language._
import java.io.File

import doobie.world._
import doobie.world.connection.{ Action => DBIO, success => dbio, _ }
import doobie.world.database.ConnectInfo
import doobie.world.resultset.stream


object Test2 extends SafeApp with ExperimentalSytax with std.default {

  // An in-memory database
  val ci = ConnectInfo[org.h2.Driver]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")

  // Some model types
  case class Id[A](toInt: Int)
  case class CountryCode(code: String)
  case class City(id: Id[City], name: String, code: CountryCode, population: Int)

  // Parameterless, unit statement
  def loadDatabase: DBIO[Unit] =
    "RUNSCRIPT FROM 'world.sql'".asUnitStatement 

  // The `count` largest cities.
  def largestCities(count: Int): DBIO[Vector[City]] =
    """SELECT id, name, countrycode, population 
       FROM city 
       ORDER BY population DESC
    """.q0(stream[City].take(count).foldVector)

  // Find countries that speak a given language
  def speakerQuery(s: String, p: Int): DBIO[List[CountryCode]] =
    """SELECT COUNTRYCODE 
       FROM COUNTRYLANGUAGE 
       WHERE LANGUAGE = ? AND PERCENTAGE > ?
    """.q((s, p), stream[CountryCode].foldVector).map(_.toList)

  // DBIO[A] describes a computation that interacts with the database and computes
  // a value of type A. 
  def action: DBIO[String] =
    for {
      _ <- dbio(println("Loading database...")) // how about a real MonadIO?
      _ <- loadDatabase
      s <- speakerQuery("French", 30)
      _ <- s.traverse(x => dbio(println(x)))
      c <- largestCities(10)
      _ <- c.traverse(x => dbio(println(x)))
    } yield "woo!"

  // Apply connection info to a DBIO[A] to get an IO[(Log, Throwable \/ A)]
  override def runc: IO[Unit] =
    for {
      p <- action.lift(ci)
      _ <- p._1.zipWithIndex.takeRight(10).traverse(x => putStrLn(x.toString)) // last 10 log entries
      _ <- putStrLn(p._2.toString) // the answer
    } yield ()

}

//////////


trait ExperimentalSytax {

  implicit class strSyntax(s: String) {

    def asUnitStatement: DBIO[Unit] =
      statement.execute.lift(s)

    def q[I : Composite, A](i: I, a: resultset.Action[A]): DBIO[A] =
      (Composite[I].set(i) >> a.lift).lift(s)

    def q0[A](a: resultset.Action[A]): DBIO[A] =
      a.lift.lift(s)

  }

}
