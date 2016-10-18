package controllers

import play.api.mvc.{Action, Controller}

class Application extends Controller {
  def index = Action {Ok(views.html.main())}

  def upload = Action(parse.multipartFormData) { request =>
    request.body.file("picture").map { picture =>
      import java.io.File
      val filename = picture.filename
      val contentType = picture.contentType
      picture.ref.moveTo(new File(s"/tmp/picture/$filename"))
      Ok("File uploaded")
    }.getOrElse {
      Redirect(routes.Application.index()).flashing(
        "error" -> "Missing file")
    }
  }
}
