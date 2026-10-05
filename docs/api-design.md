# API Design — Florida Mental Health App

TODO Day 1. One row per endpoint: method, path, required role,
request schema, response schema, status codes.

The controller stubs already carry the full endpoint list in their
header comments — transcribe from:
  AuthController, ProfileController, CatalogController,
  ProviderController, ReferralController, ReportController

Base URL: http://localhost:8080/api
Auth: Bearer token in the Authorization header on everything except
      /api/auth/register and /api/auth/login
