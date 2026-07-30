const backendPort = process.env['FINANCE_BACKEND_PORT'] || 8080;

module.exports = {
  '/api': {
    target: `http://localhost:${backendPort}`,
    secure: false,
  },
};
