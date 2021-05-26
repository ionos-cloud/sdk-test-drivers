/*
error: {
  message: '',
  response: {
    statusCode: 400,
    headers: {},
    body: ''
  }
},
httpResponse: {
    statusCode: 400,
    headers: {},
    body: ''
}
result: {}
 */

const DRAIN_TIMEOUT = 200

module.exports = {
  error: async (msg, stack = {}, code = 1) => {
    let drained = false
    process.stdout.write(JSON.stringify({
      error: {
        message: msg,
        apiResponse: null,
        stackTrace: stack
      },
      httpResponse: null,
      result: null
    }), () => { drained = true })

    do {
      await new Promise((res, _) => setTimeout(res, DRAIN_TIMEOUT))
    } while (drained === false)

    process.exit(code)
  },

  apiError: async (response) => {

    let message
    if (response.data.messages !== undefined) {
      if (response.data.messages.length === 1) {
        message = response.data.messages[0].message
      } else {
        message = 'multiple API errors'
      }
    } else {
      message = 'API Error'
    }

    let drained = false
    process.stdout.write(JSON.stringify({
      error: {
        message,
        apiResponse: {
          statusCode: response.status,
          headers: response.headers,
          body: response.data
        }
      },
      httpResponse: {
        statusCode: response.status,
        headers: response.headers,
        body: response.data
      },
      result: response.data
    }), () => { drained = true })

    do {
      await new Promise((res, _) => setTimeout(res, DRAIN_TIMEOUT))
    } while (drained === false)

  },

  success: async (response) => {
    let drained = false
    process.stdout.write(
      JSON.stringify({
        error: null,
        httpResponse: {
          statusCode: response.status,
          headers: response.headers,
          body: response.data
        },
        result: response.data
      }), () => { drained = true }
    )

    do {
      await new Promise((res, _) => setTimeout(res, DRAIN_TIMEOUT))
    } while (drained === false)
  }

}
