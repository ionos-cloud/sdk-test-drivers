const fs = require('fs');
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
    fs.writeSync(1, JSON.stringify({
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

    /* using fs.writeFileSync with fs.openSync instead of process.stdout.write to
     * ensure stdout is blocking and node wont exit before all output is flushed as it
     * does when using process.stdout.write, trimming the output to 8k.
     */
    fs.writeSync(1, JSON.stringify({
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
    process.exit(0)
  },

  success: (response) => {
    fs.writeSync(1,
      JSON.stringify({
        error: null,
        httpResponse: {
          statusCode: response.status,
          headers: response.headers,
          body: response.data
        },
        result: response.data
      })
    )
    process.exit(0)
  }

}
