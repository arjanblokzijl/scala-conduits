This is a direct port to Scala of Micheal Snoyman's [Conduit library](https://github.com/snoyberg/conduit). See
Conduits are an approach to the streaming data problem. It is meant as an
alternative to enumerators/iterators.

[![Build Status](https://secure.travis-ci.org/arjanblokzijl/scala-conduits.png)](http://travis-ci.org/arjanblokzijl/scala-conduits)

The most up-to-date documentation is available as an appendix of the Yesod
book, at:
[http://www.yesodweb.com/book/conduits](http://www.yesodweb.com/book/conduits). Below follows a brief description
of the core concepts.

## Summary
Streaming data allows us to process large datasets without pulling
all values into memory.
Conduits allow us to process large streams of data while retaining deterministic resource handling
They provide a unified interface for data streams, whether they come from files, sockets, or memory.

A Monad Transformer, ResourceT, can be used to safely allocate resources,
knowing that they will always be reclaimed- even in the presence of exceptions.

### Core classes

* **Source.**
A producer of data. The data could be in a file, coming from a socket, or in memory as a list.
To access this data, we pull from the source.

* **Sink.**
A consumer of data. Basic examples would be a sum function (adding up a stream of numbers fed in),
a file sink (which writes all incoming bytes to a file), or a socket.
We push data into a sink. When the sink finishes processing it returns some value.

* **Conduit.**
A transformer of data. The simplest example is a map function.
Like a sink, we push data into a conduit, but instead of returning a single value at the end,
a conduit can return multiple outputs every time it is pushed to.

