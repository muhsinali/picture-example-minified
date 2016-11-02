package controllers

import javax.inject.Inject

import com.google.common.io.Files
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, Macros}


//TODO how do I used DI instead of these two imports?
import play.api.Play.current
import play.api.i18n.Messages.Implicits._

import scala.concurrent.{ExecutionContext, Future}


case class PlaceData(name: String)

case class Place(name: String, picture: Array[Byte])

object Place {
  implicit val formatter = Macros.handler[Place]
}



class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext) extends Controller
  with MongoController with ReactiveMongoComponents {

  def index = Action.async {implicit request =>
    retrieveAllPlaces.map(places => Ok(views.html.main(places, Application.createPlaceForm)))
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
    val boundForm = Application.createPlaceForm.bindFromRequest()
    boundForm.fold(
    formWithErrors => {
     // TODO pass in formWithErrors into index method so that the form is populated with the values.
     Future(Redirect(routes.Application.index()).flashing("error" -> "Could not upload place. Please correct the form below."))
    },
    placeData => {
      placesFuture.flatMap(places => {
        request.body.file("picture").map { picture =>
          places.insert(Place(placeData.name, Files.toByteArray(picture.ref.file)))
          Future(Redirect(routes.Application.index()).flashing("success" -> "Successfully added place"))
        }.getOrElse {
          Future(Redirect(routes.Application.index()).flashing("error" -> "Could not upload place. Please correct the form below."))
        }
      })
    })
  }
}

object Application {
  val createPlaceForm = Form(
    mapping(
      "name" -> nonEmptyText
    )(PlaceData.apply)(PlaceData.unapply)
  )
}
