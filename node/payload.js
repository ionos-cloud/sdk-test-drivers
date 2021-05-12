const utils = require('./utils')

exports.parse = async () => {
  let payload
  try {
    payload = JSON.parse(await utils.getStdin())
  } catch(error) {
    throw new Error(`JSON decoding error: ${error.message}`)
  }

  const operation = payload['operation']
  const params = payload['params']

  if (payload['operation'] === undefined) {
    throw new Error('operation not specified in payload')
  }

  if (payload['params'] === undefined) {
    throw new Error('params not specified in payload')
  }

  return {operation, params}

}
