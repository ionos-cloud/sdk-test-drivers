const sdk = require('@ionos-cloud/sdk-nodejs')
const utils = require('./utils')

const WAIT_FOR_REQUEST_OP = 'waitForRequest'

class Api {
  constructor(name, methodName) {
    this.name = name
    this.methodName = methodName
  }

  static find(operation) {

    if (operation === WAIT_FOR_REQUEST_OP) {
      return new Api(undefined, WAIT_FOR_REQUEST_OP)
    }

    for (const key of Object.keys(sdk)) {
      if (key.endsWith('Api')) {
        for (const methodName of Object.getOwnPropertyNames(sdk[key].prototype)) {
          if (operation !== methodName) {
            continue
          }
          return new Api(key, methodName)
        }
      }
    }

    throw new Error(`operation ${operation} not found`)
  }

  async runWaitForRequest(config, params) {
    const requestParam = params.find(p => p.name === 'request')
    if (requestParam === undefined) {
      throw new Error('"request" parameter not provided for waitForRequest')
    }
    return await sdk.waitForRequest(config, requestParam.value)
  }

  async runApiMethod(config, params) {
    const apiInstance = new sdk[this.name](config)
    const method = apiInstance[this.methodName]

    /*
    const argNames = utils.getFuncArgs(method)
    const runArgs = []

    for (const argName of argNames) {
      const param = params.find(p => p.name === argName)
      if (param === undefined) {
        runArgs.push(undefined)
      } else {
        runArgs.push(param.value)
      }
    }
     */
    const requestParams = {}
    for (const param of params) {
      requestParams[param.name] = param.value
    }
    return await method.call(apiInstance, requestParams)
  }

  async run(config, params) {

    if (this.methodName === WAIT_FOR_REQUEST_OP) {
      return this.runWaitForRequest(config, params).then(response => this.restoreOriginalHeaders(response))
    }

    return this.runApiMethod(config, params).then(response => this.restoreOriginalHeaders(response))
  }

  /* axios is replacing the original headers with lower cased ones
   * we need the original headers because of the other sdks and tests which
   * are relying on a different case (the original one) even though the RFC states
   * that the headers should be treated as case-insensitive (that's actually
   * the reasoning behind axios' behavior)
   */
  restoreOriginalHeaders(response) {
    const headers = {}
    if (response.request !== undefined
      && response.request.res !== undefined
      && response.request.res.rawHeaders !== undefined) {

      for (let i = 0; i < response.request.res.rawHeaders.length; i += 2) {
        const header = response.request.res.rawHeaders[i]
        if (headers[header] === undefined) {
          headers[header] = []
        }
        headers[header].push(response.request.res.rawHeaders[i + 1])
      }
    } else {
      for (const header of Object.keys(response.headers)) {
        if (headers[header] === undefined) {
          headers[header] = []
        }
        headers[header].push(response.headers[header])
      }
    }
    response.headers = headers
    return response
  }
}

module.exports = Api
