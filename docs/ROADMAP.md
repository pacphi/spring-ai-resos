# Developing a Spring AI Enhanced Restaurant Booking System Employing an API-first Approach

## Roadmap

### MVP

This is just a punch-list of activities:

* [x] Create basic Maven multi-module project structure
* [x] Seed documentation — README, SPARK, RELEASE, ROADMAP
* [x] Code-generate model objects and a Feign client based on OpenAPI derivative 
  * [x] ask Claude to help generate from Postman docs 
  * [x] iterated on that — validated the spec online until it rendered Swagger interface without errors
* [x] Build a Spring Data module that encapsulates the model objects from `client` module and provides away to bootstrap a backend using an in-memory database
  * [x] need to figure out how to adapt codegen so that it produces models enhanced with Jakarta Persistence annotations.
  * [x] seed with some test data for demo convenience
* [x] Implement the MCP server — it's just function callbacks delegating to the DefaultApiClient
* [x] Implement a ReactJS chatbot — borrow from: https://github.com/pacphi/mattermost-ai-service.
* [x] Define the MCP client configuration for use with Claude desktop.

MVP is defined as being able to chat via Claude or the ReactJS chatbot. Interacting with one restaurant for bookings.

### Beyond

* [ ] Reevaluate use of ResOS API?
  * There's this: https://docs.opentable.com/  
    * [ ] create an OpenAPI derivative of that
    * although securing an API key for interacting with the OpenTable API is quite a bit more challenging

