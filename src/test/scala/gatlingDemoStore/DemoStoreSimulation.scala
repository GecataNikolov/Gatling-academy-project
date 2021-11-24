package gatlingDemoStore

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class DemoStoreSimulation extends Simulation {

  val domain = "demostore.gatling.io"
  val httpProtocol = http.baseUrl("http://" + domain)

  val categoryFeeder = csv("data/categoryDetails.csv").random
  val jsonFeederProducts = jsonFile("data/productDetails.json").random

  object CmsPages {

    def loadHomePage = {
      exec(http("Load Home Page")
             .get("/")
             .check(status.is(200))
             .check(regex("<title>Gatling Demo-Store</title>").exists)
             .check(css("#_csrf", "content").saveAs("csrfValue")))
    }

    def aboutUs = {
      exec(http("Load about us")
             .get("/about-us")
             .check(status.is(200))
             .check(substring("About Us")))
    }
  }

  object Catalog {

    object Category {

      def view = {
        feed(categoryFeeder)
          .exec(http("Load Category Page- ${categoryName}")
                  .get("/category/${categorySlug}")
                  .check(status.is(200))
                  .check(css("#CategoryName").is("${categoryName}")))
      }
    }

    object Product {

      def view = {
        feed(jsonFeederProducts)
          .exec(http("Load Products page- ${name}")
                  .get("/product/${slug}")
                  .check(status.is(200))
                  .check(css("#ProductDescription").is("${description}")))
      }
    }
  }

  val scn = scenario("DemoStoreSimulation")
    .exec(CmsPages.loadHomePage)
    .pause(2)
    .exec(CmsPages.aboutUs)
    .exec(Catalog.Category.view)
    .exec(Catalog.Product.view)
    .pause(2)
    .exec(http("Add Product to Cart")
            .get("/cart/add/19"))
    .pause(2)
    .exec(http("View Cart")
            .get("/cart/view"))
    .pause(2)
    .exec(http("Login User")
            .post("/login")
            .formParam("_csrf", "${csrfValue}")
            .formParam("username", "user1")
            .formParam("password", "pass"))
    .pause(2)
    .exec(http("Checkout")
            .get("/cart/checkout"))

  setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}