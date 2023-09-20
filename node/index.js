const sdk = require('@ionos-cloud/' + process.env['IONOS_SDK_NAME'])
const output = require('./output')
const payload = require('./payload')
const api = require('./api')

const username = process.env['IONOS_USERNAME']
const password = process.env['IONOS_PASSWORD']
const token = process.env['IONOS_TOKEN']

async function run() {
  if (sdk === undefined) {
    await output.error('IONOS_SDK_NAME env variable not found')
  }

  if ((username === undefined || password === undefined) && token === undefined) {
    await output.error('IONOS_USERNAME and IONOS_PASSWORD or IONOS_TOKEN env variables not found')
  }

  try {
    const config = new sdk.Configuration({apiKey: token, username, password})

    config.setDepth(10).setPretty(true);

    const {operation, params} = await payload.parse()
    const response = await api.find(operation).run(config, params)
    await output.success(response)

  } catch (error) {
    if (error.response === undefined) {
      await output.error(error.message, error.stack)
    } else {
      await output.apiError(error.response)
    }
  }
}

run().then()


