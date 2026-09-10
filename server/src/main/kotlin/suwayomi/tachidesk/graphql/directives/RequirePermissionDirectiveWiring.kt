package suwayomi.tachidesk.graphql.directives

import com.expediagroup.graphql.generator.directives.KotlinFieldDirectiveEnvironment
import com.expediagroup.graphql.generator.directives.KotlinSchemaDirectiveWiring
import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UserService
import suwayomi.tachidesk.server.user.requireUser

class RequirePermissionDirectiveWiring : KotlinSchemaDirectiveWiring {
    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        val originalDataFetcher = environment.getDataFetcher()
        val node =
            environment.directive
                .getArgument("node")
                ?.getValue<String>()
                ?.takeIf { it.isNotBlank() }
                ?: error("Missing permission node on ${environment.element.name}")

        environment.setDataFetcher(
            DataFetcher { env ->
                val userId = env.graphQlContext.getAttribute(Attribute.TachideskUser).requireUser()
                if (!UserService.hasPermission(userId, node)) {
                    throw ForbiddenException()
                }
                originalDataFetcher.get(env)
            },
        )
        return environment.element
    }
}
