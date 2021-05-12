const sdk = require('@ionos-cloud/sdk-node')
const utils = require('./utils')
const ApiError = require('./api-error')

class Api {
  constructor(name, methodName) {
    this.name = name
    this.methodName = methodName
  }

  static find(operation) {
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

  async run(config, params) {

    const apiInstance = new sdk[this.name](config)
    const method = apiInstance[this.methodName]

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
    return await method.call(apiInstance, ...runArgs)
  }
}

module.exports = Api
