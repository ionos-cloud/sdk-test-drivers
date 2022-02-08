const sdk = require('@ionos-cloud/'+process.env['IONOS_SDK_NAME'])

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
    const requestParam = params.find(p => p.name.toLowerCase() === 'request')
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
      /* lower case first letter */
      param.name = `${param.name[0].toLowerCase()}${param.name.substr(1)}`
      requestParams[param.name] = param.value
    }
    return await method.call(apiInstance, requestParams)
  }

  async run(config, params) {

    if (this.methodName === WAIT_FOR_REQUEST_OP) {
      return this.runWaitForRequest(config, params).then(response => this.makeHeadersArray(response))
    }

    return this.runApiMethod(config, params).then(response => this.makeHeadersArray(response))
  }

  makeHeadersArray(response) {
    const headers = {}
    for (const header of Object.keys(response.headers)) {
      if (headers[header] === undefined) {
        headers[header] = []
      }
      headers[header].push(response.headers[header])
    }
    response.headers = headers
    return response
  }
}

module.exports = Api
