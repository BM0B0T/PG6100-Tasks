package org.tsdes.usercollections

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service
import org.tsdes.usercollections.db.UserService

@Service
class MOMListener(private val userService: UserService) {
    companion object {
        private val log = LoggerFactory.getLogger(MOMListener::class.java)
    }

    @RabbitListener(queues = ["#{queue.name}"])
    fun receiveFromAMQP(userId: String) =
        if (userService.registerNewUser(userId)) log.info("Registered new user via MOM: $userId")
        else log.warn("Something went wrong registering via MOM: $userId")
}