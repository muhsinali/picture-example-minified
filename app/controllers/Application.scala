package controllers

import javax.inject.Inject

import com.google.common.io.Files
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, Macros}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._



case class Place(name: String, picture: Array[Byte])

object Place {
  implicit val formatter = Macros.handler[Place]
}



class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext) extends Controller
  with MongoController with ReactiveMongoComponents {
  def index = Action.async {implicit request =>
    retrieveAllPlaces.map(places => Ok(views.html.main(places)))
  }

  def placesFuture: Future[BSONCollection] = database.map(_.collection[BSONCollection]("places"))

  def retrieveAllPlaces: Future[List[Place]] = {
    val placesList = placesFuture.flatMap({
      _.find(BSONDocument()).
        cursor[Place](ReadPreference.Primary).
        collect[List]()
    })
    placesList
  }

  // TODO would like to use an ID that's generated internally (find out how to use ObjectID from MongodDB database)
  def findByName(name: String): Future[Option[Place]] = {
    val foundPlace = placesFuture.flatMap({
      _.find(BSONDocument("name" -> name)).one[Place](ReadPreference.Primary)
    })
    foundPlace
  }

  def getPictureOfPlace(name: String) = Action.async{
    findByName(name).map(placeOpt =>
      if (placeOpt.isDefined) Ok(placeOpt.get.picture)
      else BadRequest(s"Could not retrieve the place named $name")
    )
  }

  def upload = Action.async(parse.multipartFormData) { implicit request =>
    placesFuture.flatMap(places => {
      request.body.file("picture").map { picture =>
        places.insert(Place(picture.filename, Files.toByteArray(picture.ref.file)))
        Future(Redirect(routes.Application.index()).flashing("success" -> "Successfully added place"))
      }.getOrElse {
        Future(Redirect(routes.Application.index()).flashing("error" -> "Could not upload place. Please correct the form below."))
      }
    })
  }
}
