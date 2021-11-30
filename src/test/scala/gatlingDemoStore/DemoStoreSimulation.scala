package gatlingDemoStore

import io.gatling.core.Predef._
import io.gatling.core.feeder.{BatchableFeederBuilder, FileBasedFeederBuilder}
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.util.Random

class DemoStoreSimulation extends Simulation {

  val domain = "demostore.gatling.io"

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl("https://" + domain)

  val categoryFeeder: BatchableFeederBuilder[String]#F = csv("data/categoryDetails.csv").random
  val jsonFeederProducts: FileBasedFeederBuilder[Any]#F = jsonFile("data/productDetails.json")
    .random
  val loginDetails: BatchableFeederBuilder[String]#F = csv("data/loginDetails.csv").circular

  val rnd = new Random()

  def randomString(length: Int): String = {
    rnd.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  val initSession: ChainBuilder = exec(flushCookieJar)
    .exec(session => session.set("randomNumber", rnd.nextInt))
    .exec(session => session.set("customerLoggedIn", false))
    .exec(session => session.set("cartTotal", 0.00))
    .exec(addCookie(Cookie("sessionId", randomString(10)).withDomain(domain)))

  object CmsPages {

    def homepage: ChainBuilder = {
      exec(http("Load Home Page")
             .get("/")
             .check(status.is(200))
             .check(regex("<title>Gatling Demo-Store</title>").exists)
             .check(css("#_csrf", "content").saveAs("csrfValue")))
    }

    def aboutUs: ChainBuilder = {
      exec(http("Load About Us Page")
             .get("/about-us")
             .check(status.is(200))
             .check(substring("About Us"))
           )
    }
  }

  object Catalog {

    object Category {

      def view: ChainBuilder = {
        feed(categoryFeeder)
          .exec(http("Load Category Page - ${categoryName}")
                  .get("/category/${categorySlug}")
                  .check(status.is(200))
                  .check(css("#CategoryName").is("${categoryName}"))
                )
      }
    }

    object Product {

      def view: ChainBuilder = {
        feed(jsonFeederProducts)
          .exec(http("Load Product Page - ${name}")
                  .get("/product/${slug}")
                  .check(status.is(200))
                  .check(css("#ProductDescription").is("${description}"))
                )
      }

      def add: ChainBuilder = {
        exec(view)
          .exec(http("Add Product to Cart")
                  .get("/cart/add/${id}")
                  .check(status.is(200))
                  .check(substring("items in your cart"))
                )
          .exec(session => {
            val currentTotal = session("cartTotal").as[Double]
            val itemPrice = session("price").as[Double]
            session.set("cartTotal", currentTotal + itemPrice)
          })
      }
    }
  }

  object Checkout {

    def viewCart: ChainBuilder = {

      doIf(session => !session("customerLoggedIn").as[Boolean]){
        exec(Customer.login)
      }
      .exec(
        http("Load cart page")
          .get("/cart/view")
          .check(status.is(200))
        .check(css("#grandTotal").is("$$${cartTotal}")))
    }

    def competeCheckout: ChainBuilder = {
      exec(http("Checkout Cart")
             .get("/cart/checkout")
             .check(status.is(200))
             .check(substring("Thanks for your order! See you soon"))
           )
    }
  }

  object Customer {

    def login: ChainBuilder = {
      feed(loginDetails)
        .exec(
          http("Load login page")
            .get("/login")
            .check(status.is(200))
            .check(substring("Username")))
        .exec(http("Customer Login")
                .post("/login")
                .formParam("_csrf", "${csrfValue}")
                .formParam("username", "${username}")
                .formParam("password", "${password}")
                .check(status.is(200)))
        .exec(session => session.set("customerLoggedIn", true))
    }
  }


  val scn: ScenarioBuilder = scenario("Demo store simulation")
    .exec(initSession)
    .exec(CmsPages.homepage)
    .pause(2)
    .exec(CmsPages.aboutUs)
    .pause(2)
    .exec(Catalog.Category.view)
    .pause(2)
    .exec(Catalog.Product.add)
    .pause(2)
    .exec(Checkout.viewCart)
    .pause(2)
    .exec(Checkout.competeCheckout)


  setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}
