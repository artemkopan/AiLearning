// Bind webpack-dev-server to all interfaces so it is reachable via Docker port mapping.
config.devServer = config.devServer || {};
config.devServer.host = "0.0.0.0";
