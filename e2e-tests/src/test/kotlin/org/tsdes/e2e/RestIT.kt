package org.tsdes.e2e

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.awaitility.Awaitility
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

@Disabled
@Testcontainers
class RestIT {
    companion object {
        init {
            enableLoggingOfRequestAndResponseIfValidationFails()
            port = 80
        }

        class KDockerComposeContainer(id: String, path: File) :
            DockerComposeContainer<KDockerComposeContainer>(id, path)

        @Container
        @JvmField
        val env: KDockerComposeContainer = KDockerComposeContainer("card-game", File("../docker-compose.yml"))
            .withExposedService(
                "discovery", 8500,
                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(300))
            )
            .withLogConsumer("cards_0") { print("[CARD_0] " + it.utf8String) }
            .withLogConsumer("cards_1") { print("[CARD_1] " + it.utf8String) }
            .withLogConsumer("user-collections") { print("[USER_COLLECTIONS] " + it.utf8String) }
            .withLogConsumer("scores") { print("[SCORES] " + it.utf8String) }
            .withLocalCompose(true)

        @BeforeAll
        @JvmStatic
        fun waitForServers() {

            Awaitility.await().atMost(240, TimeUnit.SECONDS)
                .pollDelay(Duration.ofSeconds(20))
                .pollInterval(Duration.ofSeconds(10))
                .ignoreExceptions()
                .until {
                    given().baseUri("http://${env.getServiceHost("discovery", 8500)}")
                        .port(env.getServicePort("discovery", 8500))
                        .get("/v1/agent/services")
                        .then()
                        .body("size()", CoreMatchers.equalTo(5))
                    true
                }
        }

        @AfterAll
        @JvmStatic
        fun afterTests() {
            env.stop()
        }
    }

    @Test
    fun testGetCollection() {
        Awaitility.await().atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(10))
            .ignoreExceptions()
            .until {
                given().get("/api/cards/collection_v1_000")
                    .then()
                    .statusCode(200)
                    .body("data.cards.size", Matchers.greaterThan(10))
                true
            }
    }

    @Test
    fun testGetScores() {
        Awaitility.await().atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(10))
            .ignoreExceptions()
            .until {
                given().accept(ContentType.JSON)
                    .get("/api/scores")
                    .then()
                    .statusCode(200)
                    .body("data.list.size()", Matchers.greaterThanOrEqualTo(0))
                true
            }
    }

    @Test
    fun testCreateUser() {
        Awaitility.await().atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(10))
            .ignoreExceptions()
            .until {
                val id = "foo_testCreateUser_" + System.currentTimeMillis()
                val password = "123456"

                given().get("/api/user-collections/$id")
                    .then()
                    .statusCode(401)

                val cookie = given().contentType(ContentType.JSON)
                    .body("""{"userId": "$id","password": "$password"}""".trimIndent())
                    .post("/api/auth/signUp")
                    .then()
                    .statusCode(201)
                    .header("Set-Cookie", CoreMatchers.not(CoreMatchers.equalTo(null)))
                    .extract().cookie("SESSION")

                given().cookie("SESSION", cookie)
                    .put("/api/user-collections/$id")
                    .then()
                    .statusCode(400)

                given().cookie("SESSION", cookie)
                    .get("/api/user-collections/$id")
                    .then()
                    .statusCode(200)

                true
            }
    }

    @Test
    fun testUserCollectionAccessControl() {
        val alice = "alice_testUserCollectionAccessControl_" + System.currentTimeMillis()
        val eve = "eve_testUserCollectionAccessControl_" + System.currentTimeMillis()

        given().get("/api/user-collections/$alice").then().statusCode(401)
        given().put("/api/user-collections/$alice").then().statusCode(401)
        given().patch("/api/user-collections/$alice").then().statusCode(401)

        val cookie = given().contentType(ContentType.JSON)
            .body("""{"userId": "$eve","password": "123456"}""".trimIndent())
            .post("/api/auth/signUp")
            .then()
            .statusCode(201)
            .header("Set-Cookie", CoreMatchers.not(CoreMatchers.equalTo(null)))
            .extract().cookie("SESSION")

        given().cookie("SESSION", cookie)
            .get("/api/user-collections/$alice")
            .then()
            .statusCode(403)
    }

    @Test
    fun testAMQPSignUp() {
        Awaitility.await().atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(10))
            .ignoreExceptions()
            .until {
                val id = "foo_testCreateUser_" + System.currentTimeMillis()
                val password = "123456"
                given().get("/api/auth/user")
                    .then()
                    .statusCode(401)

                given().put("/api/user-collections/$id")
                    .then()
                    .statusCode(401)

                given().get("/api/scores/$id")
                    .then()
                    .statusCode(404)

                val cookie = given().contentType(ContentType.JSON)
                    .body("""{"userId": "$id","password": "$password"}""".trimIndent())
                    .post("/api/auth/signUp")
                    .then()
                    .statusCode(201)
                    .header("Set-Cookie", CoreMatchers.not(CoreMatchers.equalTo(null)))
                    .extract().cookie("SESSION")

                given().cookie("SESSION", cookie)
                    .get("/api/auth/user")
                    .then()
                    .statusCode(200)

                Awaitility.await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofSeconds(2))
                    .ignoreExceptions()
                    .until {
                        given().cookie("SESSION", cookie)
                            .get("/api/user-collections/$id")
                            .then()
                            .statusCode(200)

                        given().get("/api/scores/$id")
                            .then()
                            .statusCode(200)
                            .body("data.score", CoreMatchers.equalTo(0))
                        true
                    }
                true
            }
    }

}