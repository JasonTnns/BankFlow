'use strict';

function createRequestLogger(log = (line) => process.stdout.write(`${line}\n`)) {
  return function requestLogger(req, res, next) {
    const startedAt = process.hrtime.bigint();
    const { method, path } = req; // captured now: routers rewrite req.url while handling

    res.on('finish', () => {
      const ms = Number(process.hrtime.bigint() - startedAt) / 1e6;
      log(
        JSON.stringify({
          t: new Date().toISOString(),
          method,
          path,
          status: res.statusCode,
          ms: Math.round(ms),
        })
      );
    });
    next();
  };
}

module.exports = { createRequestLogger };