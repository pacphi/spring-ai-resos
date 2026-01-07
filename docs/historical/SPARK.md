# Developing a Spring AI Enhanced Restaurant Booking System Employing an API-first Approach

* [ResOS API OpenAPI Specification Proposal](#resos-api-openapi-specification-proposal)
  * [Initial Request](#initial-request)
  * [Development Process](#development-process)
    * [Phase 1: Initial Specification](#phase-1-initial-specification)
    * [Phase 2: Complete API Coverage](#phase-2-complete-api-coverage)
    * [Phase 3: Enhanced Validation and Error Handling](#phase-3-enhanced-validation-and-error-handling)
  * [Key Features of the Specification](#key-features-of-the-specification)
  * [Technical Details](#technical-details)
  * [Implementation Considerations](#implementation-considerations)
  * [Next Steps](#next-steps)
  * [Draft OpenAPI Specification](#draft-openapi-specification)

## ResOS API OpenAPI Specification Proposal

This document summarizes a conversation with [Claude](https://claude.ai) that focused on creating an [OpenAPI](https://www.openapis.org/what-is-openapi) specification for the [ResOS](https://resos.com/) API v1.2.

### Initial Request

The effort began with a request to create an OpenAPI YAML configuration that encapsulates all the ResOS API v1.2 calls and domain model objects. The source documentation was originally referenced from a [Postman documentation URL](https://documenter.getpostman.com/view/3308304/SzzehLGp?version=latest).

### Development Process

#### Phase 1: Initial Specification

The first phase focused on creating a basic OpenAPI 3.0 specification with:
- Authentication scheme using HTTP Basic Auth
- Core data models (Booking, Guest, Restaurant, Table, Area)
- Essential endpoints for booking management:
  - Listing bookings with pagination and filtering
  - Getting specific bookings
  - Creating new bookings
  - Updating bookings
  - Adding notes to bookings

#### Phase 2: Complete API Coverage

The specification was expanded to include all endpoints and models:

1. Additional Models:
   - Customer
   - OpeningHours
   - Order
   - Feedback

2. New Endpoints:
   - /healthcheck
   - /customers (list and get)
   - /tables (list)
   - /opening-hours (list and get)
   - /orders (list and get)
   - /feedback (list and get)
   - /bookings/available-dates
   - /bookings/available-times
   - /bookings/{id}/comment

3. Components:
   - Common response headers (RetryAfter for rate limiting)
   - Extended schema definitions
   - Additional path parameters
   - Query parameters

#### Phase 3: Enhanced Validation and Error Handling

The specification was further improved with:

1. Comprehensive Error Schemas:
   - Generic Error schema for all error responses
   - Specific ValidationError schema for validation failures
   - Detailed error codes and descriptions

2. Enhanced Data Validation:
   - Stricter validation rules for contact information
   - Format validation for emails and phone numbers
   - Maximum/minimum values for numeric fields
   - Length constraints for text fields

3. Improved Documentation:
   - Detailed field descriptions
   - Status code explanations
   - Business logic documentation
   - Rate limiting details

### Key Features of the Specification

1. Authentication:
   - HTTP Basic Auth using API key
   - Detailed authentication process documentation
   - Security scheme definitions

2. Rate Limiting:
   - 100 requests per second limit
   - Retry-After header implementation
   - Rate limit error responses

3. Data Models:
   - Comprehensive schema definitions
   - Required field specifications
   - Data type validations
   - Field constraints and formats

4. Error Handling:
   - Standardized error response format
   - Detailed error codes
   - Validation error specifics
   - Business logic error handling

5. Pagination and Filtering:
   - Skip/limit pagination
   - Sorting capabilities
   - Custom query support
   - Search expressions

### Technical Details

The specification includes:

1. Base Configuration:
   ```yaml
   openapi: 3.0.0
   info:
     title: ResOS API
     version: '1.2'
   servers:
     - url: https://api.resos.com/v1
   ```

2. Security Scheme:
   ```yaml
   security:
     - basicAuth: []
   components:
     securitySchemes:
       basicAuth:
         type: http
         scheme: basic
   ```

3. Error Schema Example:
   ```yaml
   Error:
     type: object
     required:
       - code
       - message
     properties:
       code:
         type: string
         enum:
           - INVALID_REQUEST
           - AUTHENTICATION_ERROR
           # ... other error codes
       message:
         type: string
   ```

### Implementation Considerations

1. Security:
   - API keys must be kept secure
   - Backend-only implementation recommended
   - CORS restrictions in place

2. Rate Limiting:
   - Fixed-time reset window
   - Proper header handling required
   - Retry mechanism recommended

3. Data Validation:
   - Client-side validation recommended
   - Server enforces all constraints
   - Proper error handling required

### Next Steps

Potential areas for further enhancement:

1. Additional validation rules for specific business cases
2. More detailed error scenarios and responses
3. Enhanced documentation for implementation
4. Example request/response pairs for each endpoint
5. Integration test scenarios

### Draft OpenAPI Specification

```yaml
openapi: 3.0.0
info:
  title: ResOS API
  version: '1.2'
  description: |
    ResOS API for managing restaurant bookings, tables, opening hours, orders, feedback and customers.
    
    For support or feature requests, contact: hi@resos.com
  contact:
    email: hi@resos.com

servers:
  - url: https://api.resos.com/v1
    description: Production API Server

security:
  - basicAuth: []

components:
  securitySchemes:
    basicAuth:
      type: http
      scheme: basic
      description: |
        Authentication using API key as username (no password required).
        The API key must be base64 encoded with a trailing colon.

  schemas:
    Error:
      type: object
      required:
        - code
        - message
      properties:
        code:
          type: string
          description: Machine-readable error code
          enum:
            - INVALID_REQUEST
            - AUTHENTICATION_ERROR
            - AUTHORIZATION_ERROR
            - RESOURCE_NOT_FOUND
            - VALIDATION_ERROR
            - RATE_LIMIT_EXCEEDED
            - INTERNAL_ERROR
            - CONFLICT
            - SERVICE_UNAVAILABLE
        message:
          type: string
          description: Human-readable error message
        details:
          type: object
          description: Additional error details specific to the error type

    ValidationError:
      type: object
      required:
        - code
        - message
        - fields
      properties:
        code:
          type: string
          enum: [VALIDATION_ERROR]
        message:
          type: string
        fields:
          type: array
          items:
            type: object
            required:
              - field
              - message
            properties:
              field:
                type: string
              message:
                type: string
                
    Customer:
      type: object
      properties:
        _id:
          type: string
        name:
          type: string
        email:
          type: string
          format: email
        phone:
          type: string
          pattern: '^\+[0-9]+$'
        createdAt:
          type: string
          format: date-time
        lastBookingAt:
          type: string
          format: date-time
        bookingCount:
          type: integer
        totalSpent:
          type: number
          format: float
        metadata:
          type: object
          description: "Custom metadata associated with the customer"

    OpeningHours:
      type: object
      properties:
        _id:
          type: string
        name:
          type: string
        date:
          type: string
          format: date
        opens:
          type: string
          pattern: '^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$'
        closes:
          type: string
          pattern: '^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$'
        isOpen:
          type: boolean
        maxPeople:
          type: integer
        metadata:
          type: object

    Order:
      type: object
      properties:
        _id:
          type: string
        bookingId:
          type: string
        items:
          type: array
          items:
            type: object
            properties:
              name:
                type: string
              quantity:
                type: integer
              price:
                type: number
              notes:
                type: string
        status:
          type: string
          enum: [pending, confirmed, completed, cancelled]
        totalAmount:
          type: number
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    Feedback:
      type: object
      properties:
        _id:
          type: string
        bookingId:
          type: string
        rating:
          type: integer
          minimum: 1
          maximum: 5
        comment:
          type: string
        createdAt:
          type: string
          format: date-time
        customer:
          $ref: '#/components/schemas/Customer'
        isPublic:
          type: boolean
          default: false

    Guest:
      type: object
      required:
        - name
      description: |
        Represents a guest making a booking. Contains all necessary contact information
        and notification preferences for the guest.
      properties:
        name:
          type: string
          example: "Thomas A. Anderson"
          minLength: 2
          maxLength: 100
          description: "Full name of the guest making the booking"
        email:
          type: string
          format: email
          example: "neo@resos.com"
          description: "Valid email address for booking confirmations and updates"
          pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$'
        phone:
          type: string
          pattern: '^\\+[1-9][0-9]{6,14}$'
          example: "+13115552368"
          description: |
            Phone number in E.164 format, including country code with + prefix.
            Must contain only numbers after the + symbol.
        notificationSms:
          type: boolean
          default: false
          description: |
            When true, guest will receive booking confirmations and updates via SMS.
            Requires a valid phone number to be provided.
        notificationEmail:
          type: boolean
          default: false
          description: |
            When true, guest will receive booking confirmations and updates via email.
            Requires a valid email address to be provided.

    Address:
      type: object
      properties:
        address1:
          type: string
        address2:
          type: string
        city:
          type: string
        postalCode:
          type: integer
        countryCode:
          type: string
          example: "DK"
        country:
          type: string
          example: "Denmark"

    Restaurant:
      type: object
      properties:
        name:
          type: string
        address:
          $ref: '#/components/schemas/Address'
        email:
          type: string
          format: email
        phone:
          type: string
        slug:
          type: string
        languageCode:
          type: string
          enum: [en, es, de, da, sv]
          default: en

    Area:
      type: object
      properties:
        _id:
          type: string
        name:
          type: string
        internalNote:
          type: string
        bookingPriority:
          type: integer

    Table:
      type: object
      properties:
        _id:
          type: string
        name:
          type: string
        seatsMin:
          type: integer
          minimum: 1
        seatsMax:
          type: integer
        area:
          $ref: '#/components/schemas/Area'
        internalNote:
          type: string

    Booking:
      type: object
      required:
        - date
        - time
        - people
        - duration
        - guest
        - openingHourId
      description: |
        Represents a restaurant booking. Contains all necessary information about
        the reservation, including guest details, timing, and table allocation.
      properties:
        _id:
          type: string
          description: "Unique identifier for the booking"
          readOnly: true
        date:
          type: string
          format: date
          example: "2021-09-18"
          description: "Date of the booking in YYYY-MM-DD format"
        time:
          type: string
          pattern: '^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$'
          example: "21:00"
          description: "Time of the booking in 24-hour HH:mm format"
        people:
          type: integer
          minimum: 1
          maximum: 999
          description: "Number of people in the booking party"
        duration:
          type: integer
          minimum: 15
          maximum: 480
          description: |
            Duration of the booking in minutes.
            Minimum 15 minutes, maximum 8 hours (480 minutes).
        status:
          type: string
          enum:
            - request
            - declined
            - approved
            - arrived
            - seated
            - left
            - no_show
            - canceled
            - waitlist
          default: request
          description: |
            Current status of the booking:
            * request - Initial booking request
            * declined - Booking was declined by the restaurant
            * approved - Booking was approved by the restaurant
            * arrived - Guest has arrived at the restaurant
            * seated - Guest has been seated at their table
            * left - Guest has finished their meal and left
            * no_show - Guest did not show up for their booking
            * canceled - Booking was canceled by the guest
            * waitlist - Booking is on the waitlist
        tables:
          type: array
          items:
            $ref: '#/components/schemas/Table'
          description: "Array of tables allocated for this booking"
        metadata:
          type: object
          description: |
            Contains any arbitrary metadata for the booking.
            Maximum total length of stringified JSON is 2000 characters.
          maxLength: 2000
        comments:
          type: array
          items:
            type: string
          description: "Array of customer-visible comments on the booking"
        guest:
          $ref: '#/components/schemas/Guest'
        source:
          type: string
          enum:
            - walkin
            - website
            - phone
            - email
            - message
            - eatie
            - other
          default: other
          description: |
            Source of the booking:
            * walkin - Guest walked in without a booking
            * website - Booked through the restaurant's website
            * phone - Booked via phone call
            * email - Booked via email
            * message - Booked via messaging service
            * eatie - Booked through Eatie platform
            * other - Other booking source
        openingHourId:
          type: string
          description: "Reference to the opening hours period this booking is for"
        createdAt:
          type: string
          format: date-time
          readOnly: true
          description: "Timestamp when the booking was created"
        activityAt:
          type: string
          format: date-time
          readOnly: true
          description: "Timestamp of the last activity on this booking"
        sendNotification:
          type: boolean
          description: "Whether notifications should be sent for this booking"
        payment:
          type: object
          description: |
            Payment information for bookings with prepayment or no-show fee.
            Structure depends on the payment provider used.
        comment:
          type: string
          maxLength: 1000
          description: |
            Public comment written by guest.
            Visible to both the guest and the restaurant.
        note:
          type: string
          maxLength: 1000
          description: "Internal note only visible to the restaurant"
        noteAuthor:
          type: string
          default: "API"
          maxLength: 100
          description: "Name of the person who wrote the internal note"
        referrer:
          type: string
          format: uri
          maxLength: 2000
          description: "URL of website that referred the booking"
        languageCode:
          type: string
          enum: [en, es, de, da, sv]
          default: en
          description: |
            Language code for guest communications:
            * en - English
            * es - Spanish
            * de - German
            * da - Danish
            * sv - Swedish
        restaurant:
          $ref: '#/components/schemas/Restaurant'

  parameters:
    fromDateTime:
      name: fromDateTime
      in: query
      description: ISO date format (ISO 8601) in UTC
      required: false
      schema:
        type: string
        format: date
        example: "2021-04-17"

    toDateTime:
      name: toDateTime
      in: query
      description: ISO date format (ISO 8601) in UTC
      required: false
      schema:
        type: string
        format: date
        example: "2021-04-18"

    limit:
      name: limit
      in: query
      description: Number of records to return (max 100)
      required: false
      schema:
        type: integer
        default: 100
        maximum: 100

    skip:
      name: skip
      in: query
      description: Number of records to skip
      required: false
      schema:
        type: integer
        default: 0

    sort:
      name: sort
      in: query
      description: Sort field and direction (1 ascending, -1 descending)
      required: false
      schema:
        type: string
        example: "createdAt:1"

    customQuery:
      name: customQuery
      in: query
      description: Search expression for filtering results
      required: false
      schema:
        type: string
        example: "people>1,duration>60"

paths:
  /healthcheck:
    get:
      summary: System health check
      description: Check if the API is operational
      responses:
        '200':
          description: System is operational
        '503':
          description: System is not operational

  /customers:
    get:
      summary: List customers
      description: Retrieve a list of customers
      parameters:
        - $ref: '#/components/parameters/limit'
        - $ref: '#/components/parameters/skip'
        - $ref: '#/components/parameters/sort'
        - $ref: '#/components/parameters/customQuery'
      responses:
        '200':
          description: List of customers
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Customer'

  /customers/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string
    get:
      summary: Get customer
      description: Retrieve a specific customer by ID
      responses:
        '200':
          description: Customer details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Customer'
        '404':
          description: Customer not found

  /tables:
    get:
      summary: List tables
      description: Retrieve a list of tables
      responses:
        '200':
          description: List of tables
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Table'

  /opening-hours:
    get:
      summary: List opening hours
      description: Retrieve a list of opening hours
      responses:
        '200':
          description: List of opening hours
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/OpeningHours'

  /opening-hours/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string
    get:
      summary: Get opening hours
      description: Retrieve specific opening hours by ID
      responses:
        '200':
          description: Opening hours details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OpeningHours'
        '404':
          description: Opening hours not found

  /orders:
    get:
      summary: List orders
      description: Retrieve a list of orders
      parameters:
        - $ref: '#/components/parameters/limit'
        - $ref: '#/components/parameters/skip'
        - $ref: '#/components/parameters/sort'
        - $ref: '#/components/parameters/customQuery'
      responses:
        '200':
          description: List of orders
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Order'

  /orders/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string
    get:
      summary: Get order
      description: Retrieve a specific order by ID
      responses:
        '200':
          description: Order details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Order'
        '404':
          description: Order not found

  /feedback:
    get:
      summary: List feedback
      description: Retrieve a list of feedback
      parameters:
        - $ref: '#/components/parameters/limit'
        - $ref: '#/components/parameters/skip'
        - $ref: '#/components/parameters/sort'
        - $ref: '#/components/parameters/customQuery'
      responses:
        '200':
          description: List of feedback
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Feedback'

  /feedback/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string
    get:
      summary: Get feedback
      description: Retrieve specific feedback by ID
      responses:
        '200':
          description: Feedback details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Feedback'
        '404':
          description: Feedback not found

  /bookings:
    get:
      summary: List bookings
      description: Retrieve a list of bookings with optional filtering
      parameters:
        - $ref: '#/components/parameters/fromDateTime'
        - $ref: '#/components/parameters/toDateTime'
        - $ref: '#/components/parameters/limit'
        - $ref: '#/components/parameters/skip'
        - $ref: '#/components/parameters/sort'
        - $ref: '#/components/parameters/customQuery'
        - name: onlyConfirmed
          in: query
          description: Return only confirmed bookings
          required: false
          schema:
            type: boolean
      responses:
        '200':
          description: List of bookings
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Booking'
        '401':
          description: Unauthorized
        '429':
          description: Too many requests
          headers:
            Retry-After:
              description: Time in ms until reset
              schema:
                type: integer

    post:
      summary: Create booking
      description: Create a new booking
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Booking'
      responses:
        '200':
          description: Booking created successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Booking'
        '400':
          description: Bad request - invalid booking data
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationError'
        '401':
          description: Unauthorized - invalid or missing API key
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
        '422':
          description: Unprocessable Entity - e.g., table not available
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
        '429':
          description: Too many requests - rate limit exceeded
          headers:
            Retry-After:
              $ref: '#/components/headers/RetryAfter'
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
        '500':
          description: Internal server error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'

  /bookings/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string

    get:
      summary: Get booking
      description: Retrieve a specific booking by ID
      responses:
        '200':
          description: Booking details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Booking'
        '404':
          description: Booking not found

    put:
      summary: Update booking
      description: Update an existing booking
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Booking'
      responses:
        '200':
          description: Booking updated successfully
          content:
            application/json:
              schema:
                type: boolean
        '404':
          description: Booking not found

  /bookings/available-dates:
    get:
      summary: Get available booking dates
      description: Retrieve dates available for booking
      responses:
        '200':
          description: List of available dates
          content:
            application/json:
              schema:
                type: array
                items:
                  type: string
                  format: date

  /bookings/available-times:
    get:
      summary: Get available booking times
      description: Retrieve available times for a specific date
      parameters:
        - name: date
          in: query
          required: true
          schema:
            type: string
            format: date
        - name: people
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
      responses:
        '200':
          description: List of available time slots
          content:
            application/json:
              schema:
                type: array
                items:
                  type: string
                  pattern: '^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$'

  /bookings/{id}/comment:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string
    post:
      summary: Add comment to booking
      description: Add a customer-visible comment to an existing booking
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - text
              properties:
                text:
                  type: string
      responses:
        '200':
          description: Comment added successfully
          content:
            application/json:
              schema:
                type: string
                description: Comment ID
        '404':
          description: Booking not found

  /bookings/{id}/restaurantNote:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string

    post:
      summary: Add note to booking
      description: Add an internal note to an existing booking
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - text
              properties:
                text:
                  type: string
      responses:
        '200':
          description: Note added successfully
          content:
            application/json:
              schema:
                type: string
                description: Note ID
        '404':
          description: Booking not found
```