const INDENT = 2

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

module.exports = {
  error: (msg, stack = {}, code = 1) => {
    process.stdout.write(JSON.stringify({
      error: {
        message: msg,
        apiResponse: null,
        stackTrace: stack
      },
      httpResponse: null,
      result: null
    }))
    process.exit(code)
  },

  apiError: (response, code = 1) => {

    let message = ''
    if (response.data.messages !== undefined) {
      if (response.data.messages.length === 1) {
        message = response.data.messages[0].message
      } else {
        message = 'multiple API errors'
      }
    } else {
      message = 'API Error'
    }

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
    }))
    process.exit(1)
  },

  success: (response) => {
    process.stdout.write(JSON.stringify({
      error: null,
      httpResponse: {
        statusCode: response.status,
        headers: response.headers,
        body: response.data
      },
      result: response.data
    }))
    process.exit(0)
  }

}
