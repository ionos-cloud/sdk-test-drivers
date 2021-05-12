const sdk = require('@ionos-cloud/sdk-node')
const output = require('./output')
const payload = require('./payload')
const api = require('./api')

const username = process.env['IONOS_USERNAME']
const password = process.env['IONOS_PASSWORD']

if (username === undefined) {
  output.error('IONOS_USERNAME env variable not found')
}

if (password === undefined) {
  output.error('IONOS_PASSWORD env variable not found')
}

(async function() {

  try {
    const config = new sdk.Configuration({username, password});

    const {operation, params} = await payload.parse()
    const response = await api.find(operation).run(config, params)
    output.success(response)

  } catch(error) {
    if (error.response === undefined) {
      output.error(error.message)
    } else {
      output.apiError(error.response)
    }
  }


})()

