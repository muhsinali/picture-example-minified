package controllers

import javax.inject.Inject

import com.google.common.io.Files
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, Macros}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

case class Person(name: String, age: Int, picture: Array[Byte])

object Person{
  implicit val formatter = Macros.handler[Person]
}

class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext) extends Controller
  with MongoController with ReactiveMongoComponents {
  def index = Action {implicit request =>
    Ok(views.html.main(Await.result(retrieveAllPeople, 1 seconds)))
  }

  def peopleFuture: Future[BSONCollection] = database.map(_.collection[BSONCollection]("pictures"))

  // TODO copy the code that you have in your Scala main project.
  // TODO The aim here is to show the pictures that you've managed to store in the database.
  def retrieveAllPeople: Future[List[Person]] = {
    val peopleList: Future[List[Person]] = peopleFuture.flatMap({
      _.find(BSONDocument()).
        cursor[Person](ReadPreference.Primary).
        collect[List]()
    })
    peopleList
  }

  def findByName(name: String): Future[Option[Person]] = {
    val foundPerson = peopleFuture.flatMap({
      _.find(BSONDocument("name" -> name)).one[Person](ReadPreference.Primary)
    })
    foundPerson
  }

  def getPictureOfPerson(name: String) = Action.async{
    findByName(name).map(personOpt =>
      if (personOpt.isDefined) Ok(personOpt.get.picture)
      else BadRequest("Uh oh")
    )
//    val foundPerson: Person = findByName(name)
//    if (foundPerson != null) Ok(foundPerson.picture)
//    else BadRequest()
  }

  def upload = Action.async(parse.multipartFormData) { implicit request =>
    peopleFuture.flatMap(pictures => {
      request.body.file("picture").map { picture =>
        import java.io.File
        println(s"${picture.getClass}")
        val filename = picture.filename
        val contentType = picture.contentType
        pictures.insert(Person(picture.filename, 21, Files.toByteArray(picture.ref.file)))
        //pictures.insert(BSONDocument("name" -> "Joe Bloggs", "age" -> 21, "picture" -> Files.toByteArray(picture.ref.file)))
        picture.ref.moveTo(new File(s"/Users/msa110/Desktop/Desktop_stuff/Pictures/Places/$filename"))
        Future(Redirect(routes.Application.index()))
      }.getOrElse {
        Future(Redirect(routes.Application.index()))
      }
    })
  }
}
