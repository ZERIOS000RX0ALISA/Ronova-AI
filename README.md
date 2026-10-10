# Ronova AI

A multi-provider AI chat client designed to connect to local inference
servers and hosted AI APIs.

## Overview

Ronova AI is an AI chat application project focused on providing a
unified interface for interacting with different AI backends.

The project is designed to support connections to locally hosted language
models as well as compatible cloud-based AI services.

Its long-term direction includes expanding the chat experience with
additional tools and productivity features.

## Goals

The project aims to explore:

- A unified interface for multiple AI providers.
- Connections to locally hosted language models.
- Support for hosted AI APIs.
- Flexible backend configuration.
- A foundation for future AI-assisted productivity features.

## Supported Connection Types

Ronova AI is being developed with support for the following connection
categories:

### Local AI Servers

The application is intended to connect to local inference servers,
including `llama-server`.

This allows users to interact with models hosted on their own computers,
subject to server configuration and API compatibility.

### Hosted AI Services

The project is designed to accommodate supported hosted AI providers,
including:

- OpenAI-compatible APIs.
- Google Gemini.
- Anthropic Claude.
- xAI Grok.
- Other compatible endpoints where implemented.

Provider availability depends on the current implementation, API
compatibility, credentials, and service requirements.

Listing a provider here does not imply that every model or endpoint
has been fully tested.

## Development Direction

Potential areas of continued development include:

- Expanded provider and model configuration.
- Improved conversation management.
- Document and image handling.
- Retrieval-augmented generation (RAG) workspaces.
- Richer presentation of structured responses.
- LaTeX rendering.
- Tables, charts, and other structured output.
- Improvements to reliability and user experience.

These items describe development directions and should not be interpreted
as a guarantee that every feature is currently available.

## Project Status

**Status: Early-stage development**

Ronova AI is an evolving project. Its architecture, supported providers,
feature set, and user experience may change as development continues.

Some integrations and capabilities may be incomplete or experimental.

## Requirements

The exact requirements depend on the selected backend.

Generally, users may need:

- A compatible device and operating system.
- Network access to the selected AI endpoint.
- An API key for providers that require authentication.
- A running local inference server when using a locally hosted model.

Detailed installation and configuration instructions will be added
after the current application setup and supported environments have
been verified.

## Privacy and Security

Privacy characteristics depend on the selected provider and deployment.

When using a local server, requests are sent to the configured server.
When using a hosted provider, requests may be processed by that provider
under its applicable terms and privacy policies.

Users should:

- Review the privacy policies of hosted services.
- Protect API keys and other credentials.
- Avoid committing secrets to version control.
- Verify endpoint addresses before connecting.
- Understand where prompts, files, and other submitted data are processed.

A local connection alone does not guarantee that every component of an
application operates locally.

## Technology

Ronova AI was developed with assistance from Google AI Studio.

The specific frameworks, programming languages, libraries, and API
implementations should be documented after reviewing the project source.

## Open Source

The repository is intended to make the project available for inspection
and further development.

Bug reports, feature suggestions, and contributions can help improve
the project as it matures.

## Disclaimer

Ronova AI is an independent client project. It is not an official
application of OpenAI, Google, Anthropic, xAI, or any other AI provider.

Provider names are used only to describe intended or implemented
compatibility.

Availability and behavior depend on the selected backend and its
current API.

## License

The project license will be specified in the repository's `LICENSE` file.
Until a license is added, do not assume that the repository grants
permission to redistribute or modify its contents.
