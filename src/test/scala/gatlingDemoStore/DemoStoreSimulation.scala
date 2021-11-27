package gatlingDemoStore

import io.gatling.core.Predef._
import io.gatling.core.feeder.{BatchableFeederBuilder, FileBasedFeederBuilder}
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

class DemoStoreSimulation extends Simulation {

  val domain = "demostore.gatling.io"

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl("https://" + domain)

  val categoryFeeder: BatchableFeederBuilder[String]#F = csv("data/categoryDetails.csv").random
  val jsonFeederProducts: FileBasedFeederBuilder[Any]#F = jsonFile("data/productDetails.json").random
  val loginDetails: BatchableFeederBuilder[String]#F = csv("data/loginDetails.csv").circular

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
      }
    }
  }

  object Checkout {

    def viewCart: ChainBuilder = {
      exec(
        http("Load cart page")
          .get("/cart/view")
          .check(status.is(200)))
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
    }
  }


  val scn: ScenarioBuilder = scenario("Demo store simulation")
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
    .exec(Customer.login)
    .pause(2)
    .exec(Checkout.competeCheckout)


  setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}
