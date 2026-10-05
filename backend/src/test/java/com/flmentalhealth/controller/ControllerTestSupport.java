package com.flmentalhealth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flmentalhealth.exception.GlobalExceptionHandler;
import com.flmentalhealth.security.UserPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Shared MockMvc setup for the controller tests.
 *
 * WHY STANDALONE RATHER THAN @WebMvcTest: standaloneSetup wires one
 * controller to the Spring MVC machinery with no application context at
 * all - no component scan, no datasource, no JWT secret to configure.
 * The tests start in milliseconds and they cannot fail for a reason
 * that has nothing to do with the controller under test.
 *
 * WHAT THAT MEANS FOR COVERAGE: @PreAuthorize is NOT active here,
 * because method security is applied by a Spring proxy that standalone
 * setup does not build. So these tests cover request mapping, binding,
 * validation, status codes and the exception handler - not
 * authorization. Role enforcement is verified end to end against the
 * running application in the Bruno collection (bruno/), which is the
 * right level for it: it exercises the real filter chain and the real
 * tokens rather than a mock of them.
 *
 * The one thing that does have to be faked is the signed-in user.
 * @AuthenticationPrincipal is resolved by Spring Security's own
 * resolver, which is not registered here, so PrincipalResolver below
 * stands in for it.
 */
final class ControllerTestSupport {

    private ControllerTestSupport() {}

    static final ObjectMapper JSON = new ObjectMapper();

    /** A controller wired up with the real exception handler. */
    static MockMvc mockMvcFor(Object controller, UserPrincipal principal) {
        return MockMvcBuilders.standaloneSetup(controller)
                // The real @ControllerAdvice, so status-code mapping is
                // the production mapping rather than a test double.
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PrincipalResolver(principal),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    static String json(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize test body", e);
        }
    }

    /**
     * Supplies the signed-in user for any UserPrincipal parameter.
     *
     * Custom resolvers are consulted before the catch-all
     * @ModelAttribute resolver, so this claims the parameter that
     * @AuthenticationPrincipal would normally fill.
     */
    static final class PrincipalResolver implements HandlerMethodArgumentResolver {

        private final UserPrincipal principal;

        PrincipalResolver(UserPrincipal principal) {
            this.principal = principal;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return UserPrincipal.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
            return principal;
        }
    }
}
