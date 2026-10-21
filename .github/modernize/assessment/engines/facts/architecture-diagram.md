# Architecture Diagram

PC Mania es una solución web modular para administrar un inventario y
distribuir eventos de dominio mediante AWS. La arquitectura separa el
catálogo, el panel administrativo y la mensajería, con autenticación Cognito
compartida y consumidores desacoplados por dominio.

## Application Architecture

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart TD
    subgraph Clients["Frontend clients"]
        Store["Tienda React Vite port 5173"]
        Admin["Admin React Vite port 5174"]
    end
    subgraph ProductApp["Product API Spring Boot port 8080"]
        ProductApi["Product REST API"]
        ProductSecurity["Cognito JWT security"]
        ProductService["Product service"]
        ProductMemory[("In memory product catalog")]
        EventClient["Messaging event client"]
    end
    subgraph MessagingApp["Messaging Spring Boot port 8081"]
        EventApi["Event producer REST API"]
        AdminApi["Messaging admin REST API"]
        ConsumerControl["Consumer control service"]
        NotificationConsumer["Notifications consumer"]
        TicketConsumer["Tickets consumer"]
        DocumentConsumer["Documents consumer"]
        MessagingSecurity["Cognito JWT security"]
    end
    subgraph AwsMessaging["AWS messaging services"]
        Topic["SNS pc-mania-events"]
        NotificationsQueue[("SQS notifications")]
        TicketsQueue[("SQS tickets")]
        DocumentsQueue[("SQS documents")]
        NotificationsDlq[("DLQ notifications")]
        TicketsDlq[("DLQ tickets")]
        DocumentsDlq[("DLQ documents")]
        CloudWatch["CloudWatch metrics and alarms"]
    end
    subgraph Identity["AWS identity"]
        Cognito["Amazon Cognito user pools"]
    end

    Store -->|"read catalog"| ProductApi
    Admin -->|"manage products"| ProductApi
    Admin -->|"manage messaging"| AdminApi
    ProductApi --> ProductSecurity
    ProductSecurity --> ProductService
    ProductService -->|"CRUD in memory"| ProductMemory
    ProductService -->|"product events"| EventClient
    EventClient -->|"HTTP event with Bearer token"| EventApi
    EventApi --> MessagingSecurity
    MessagingSecurity -->|"publish event"| Topic
    Topic -->|"filtered fanout"| NotificationsQueue
    Topic -->|"filtered fanout"| TicketsQueue
    Topic -->|"filtered fanout"| DocumentsQueue
    NotificationsQueue --> NotificationConsumer
    TicketsQueue --> TicketConsumer
    DocumentsQueue --> DocumentConsumer
    NotificationsQueue -->|"redrive after 3 failures"| NotificationsDlq
    TicketsQueue -->|"redrive after 3 failures"| TicketsDlq
    DocumentsQueue -->|"redrive after 3 failures"| DocumentsDlq
    AdminApi --> ConsumerControl
    AdminApi -->|"topology and DLQ operations"| AwsMessaging
    ConsumerControl --> NotificationConsumer
    ConsumerControl --> TicketConsumer
    ConsumerControl --> DocumentConsumer
    AwsMessaging --> CloudWatch
    Store -.->|"OIDC login and read token"| Cognito
    Admin -.->|"OIDC login and write token"| Cognito
    ProductSecurity -.->|"JWT issuer and keys"| Cognito
    MessagingSecurity -.->|"JWT issuer and keys"| Cognito
~~~

### Technology Stack Summary

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Frontend | React | 19.2.8 | Store and admin interfaces |
| Frontend tooling | Vite | 8.2.2 | Local development and production builds |
| Frontend language | TypeScript | 6.0.2 | Typed UI and API clients |
| Frontend auth | AWS Amplify | 6.20.0 | Cognito hosted UI and sessions |
| Product API | Spring Boot | 4.1.1 | REST API and application runtime |
| Product API security | Spring Security OAuth2 Resource Server | Spring Boot managed | JWT validation and scopes |
| Messaging API | Spring Boot | 4.1.1 | Producer, administration and consumers |
| Cloud SDK | AWS SDK for Java 2 | 2.31.63 | SNS, SQS and CloudWatch access |
| Runtime language | Java | 17 target | Backend and messaging services |
| Messaging transport | Amazon SNS | AWS managed | Event fanout and subscription filters |
| Queue transport | Amazon SQS | AWS managed | Durable delivery and retry visibility |
| Failure isolation | Amazon SQS DLQ | AWS managed | Failed message isolation |
| Observability | Amazon CloudWatch | AWS managed | Queue age metrics and DLQ alarms |
| Identity | Amazon Cognito | AWS managed | OAuth2 access tokens and scopes |

### Data Storage & External Services

The product catalog currently uses an in-memory concurrent map in the product
backend; no database is part of the agreed scope. Messaging state is stored in
SNS and SQS in AWS, with one primary queue and one DLQ per domain. Cognito
provides identity, token issuance and JWT key discovery. CloudWatch provides
queue metrics and alarms. The frontends call the local product and messaging
services directly during development.

### Key Architectural Decisions

- Product operations stay in a dedicated backend while event distribution is
  delegated to a separate messaging microservice.
- SNS handles fanout and subscription filters; SQS handles durability,
  visibility timeout, acknowledgement and redrive to DLQ.
- The store keeps `read` access, while the admin and messaging operations use
  `write`; no database or separate admin scope is introduced in this version.

## Component Relationships

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart LR
    subgraph PresentationLayer["Presentation"]
        cStoreApp["Store React application"]
        cAdminApp["Admin React application"]
        cProductController["ProductController"]
        cEventController["EventProducerController"]
        cMessagingAdminController["MessagingAdminController"]
        cConsumerAdminController["ConsumerAdminController"]
        cCloudWatchController["CloudWatchAdminController"]
    end
    subgraph BusinessLayer["Business logic"]
        cProductService["ProductoService"]
        cEventClient["MessagingEventClient"]
        cEventService["EventProducerService"]
        cMessagingAdminService["MessagingAdminService"]
        cCloudWatchService["CloudWatchAdminService"]
        cConsumerControl["ConsumerControlService"]
        cMessageHandler["Domain message handlers"]
    end
    subgraph DataLayer["Data and AWS access"]
        cProductModel["Producto model"]
        cMessagingProperties["MessagingProperties"]
        cSnsClient["AWS SNS client"]
        cSqsClient["AWS SQS client"]
        cCloudWatchClient["AWS CloudWatch client"]
        cProductMemory[("ConcurrentHashMap catalog")]
    end
    subgraph ConsumerLayer["Scheduled consumers"]
        cAbstractConsumer["AbstractSqsConsumer"]
        cNotifications["NotificationsConsumer"]
        cTickets["TicketsConsumer"]
        cDocuments["DocumentsConsumer"]
    end
    subgraph CrossCuttingLayer["Cross cutting concerns"]
        cProductSecurity["Product JWT security"]
        cMessagingSecurity["Messaging JWT security"]
        cCors["CORS configuration"]
        cJackson["Jackson configuration"]
    end

    cStoreApp -->|"REST read"| cProductController
    cAdminApp -->|"REST CRUD"| cProductController
    cAdminApp -->|"REST admin"| cMessagingAdminController
    cProductController -->|"delegates"| cProductService
    cProductService -->|"uses"| cProductModel
    cProductService -->|"stores"| cProductMemory
    cProductController -->|"publishes"| cEventClient
    cEventClient -->|"HTTP event"| cEventController
    cEventController -->|"delegates"| cEventService
    cEventService -->|"publishes"| cSnsClient
    cMessagingAdminController -->|"delegates"| cMessagingAdminService
    cMessagingAdminService -->|"topology and queues"| cSnsClient
    cMessagingAdminService -->|"queue status and DLQ"| cSqsClient
    cMessagingAdminController -->|"consumer controls"| cConsumerAdminController
    cConsumerAdminController -->|"delegates"| cConsumerControl
    cCloudWatchController -->|"delegates"| cCloudWatchService
    cCloudWatchService -->|"metrics and alarms"| cCloudWatchClient
    cAbstractConsumer -->|"polls"| cSqsClient
    cNotifications -->|"extends"| cAbstractConsumer
    cTickets -->|"extends"| cAbstractConsumer
    cDocuments -->|"extends"| cAbstractConsumer
    cNotifications -->|"handles"| cMessageHandler
    cTickets -->|"handles"| cMessageHandler
    cDocuments -->|"handles"| cMessageHandler
    cMessagingAdminService -->|"reads"| cMessagingProperties
    cEventService -->|"reads"| cMessagingProperties
    cProductSecurity -.->|"protects"| cProductController
    cMessagingSecurity -.->|"protects"| cEventController
    cMessagingSecurity -.->|"protects"| cMessagingAdminController
    cCors -.->|"browser access"| cProductController
    cCors -.->|"browser access"| cMessagingAdminController
    cJackson -.->|"serialization"| cEventClient
~~~

### Component Inventory

| Component | Layer | Type | Responsibility |
|---|---|---|---|
| Store React application | Presentation | React frontend | Authenticated product catalog with read scope |
| Admin React application | Presentation | React frontend | Product CRUD and messaging dashboard |
| ProductController | Presentation | Spring REST controller | Product listing, creation, update and deletion |
| EventProducerController | Presentation | Spring REST controller | Receives domain events for SNS publication |
| MessagingAdminController | Presentation | Spring REST controller | Topology, queue, subscription and DLQ operations |
| ConsumerAdminController | Presentation | Spring REST controller | Pause, resume and inspect consumers |
| CloudWatchAdminController | Presentation | Spring REST controller | Configure alarms and retention information |
| ProductoService | Business logic | Spring service | In-memory product CRUD and validation |
| MessagingEventClient | Business logic | HTTP client component | Sends product events to messaging |
| EventProducerService | Business logic | Spring service | Publishes events to SNS |
| MessagingAdminService | Business logic | Spring service | Creates, inspects and operates messaging resources |
| CloudWatchAdminService | Business logic | Spring service | Creates DLQ alarms and exposes retention policy |
| ConsumerControlService | Business logic | Spring service | Runtime consumer pause and resume state |
| Domain message handlers | Business logic | Handler interface | Domain-specific message processing contract |
| Producto model | Data and AWS access | Domain model | Product fields exchanged by API and events |
| MessagingProperties | Data and AWS access | Configuration properties | Topic, queues, filters and retry settings |
| AWS SNS client | Data and AWS access | AWS SDK client | Topic publishing and subscription administration |
| AWS SQS client | Data and AWS access | AWS SDK client | Polling, acknowledgement, queue and DLQ operations |
| AWS CloudWatch client | Data and AWS access | AWS SDK client | Age metrics and alarm operations |
| ConcurrentHashMap catalog | Data and AWS access | In-memory store | Current product inventory |
| AbstractSqsConsumer | Scheduled consumers | Java base component | Polling, deserialization, retry behavior and ACK |
| NotificationsConsumer | Scheduled consumers | Scheduled consumer | Product and order notification processing |
| TicketsConsumer | Scheduled consumer | Scheduled consumer | Order ticket processing |
| DocumentsConsumer | Scheduled consumer | Scheduled consumer | Document request processing |
| Product JWT security | Cross cutting concerns | Spring Security | Validates product API access tokens and scopes |
| Messaging JWT security | Cross cutting concerns | Spring Security | Validates producer and admin access tokens |
| CORS configuration | Cross cutting concerns | Spring configuration | Allows local frontends to call APIs |
| Jackson configuration | Cross cutting concerns | ObjectMapper configuration | Serializes product event payloads |
