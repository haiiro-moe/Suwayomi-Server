package suwayomi.tachidesk.graphql.directives

import com.expediagroup.graphql.generator.annotations.GraphQLDirective
import graphql.introspection.Introspection.DirectiveLocation

@GraphQLDirective(
    name = "requirePermission",
    description = "Requires an authenticated user's permission node",
    locations = [DirectiveLocation.FIELD_DEFINITION, DirectiveLocation.OBJECT],
)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequirePermission(val node: String)
