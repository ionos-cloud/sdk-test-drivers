const sdk = require('@ionos-cloud/sdk-nodejs')
const output = require('./output')
const payload = require('./payload')
const api = require('./api')

const username = process.env['IONOS_USERNAME']
const password = process.env['IONOS_PASSWORD']

async function run() {
  if (username === undefined) {
    await output.error('IONOS_USERNAME env variable not found')
  }

  if (password === undefined) {
    await output.error('IONOS_PASSWORD env variable not found')
  }

  try {
    const config = new sdk.Configuration({username, password});

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


