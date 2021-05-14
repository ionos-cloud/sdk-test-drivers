module.exports = class ApiError extends Error {
  constructor(response) {
    super('API Error');
    this.response = response
  }
}
