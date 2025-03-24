# RiskWatch Android App

## Setup Instructions

### Amazon Login Setup
1. Copy `app/src/main/assets/api_key.txt.template` to `app/src/main/assets/api_key.txt`
2. Replace the content with your Amazon API key from the Amazon Developer Console
3. Make sure not to commit your actual API key to Git

### Configuration Files
The following files contain sensitive information and are not committed to Git:
- `app/src/main/assets/api_key.txt` - Amazon Login API key
- `local.properties` - Local SDK configuration

Please obtain these files from the project administrator or set them up according to the templates provided.

### Development Setup
1. Clone the repository
2. Set up the configuration files as described above
3. Build and run the project in Android Studio

For more information about setting up Amazon Login, visit the [Login with Amazon documentation](https://developer.amazon.com/docs/login-with-amazon/documentation-overview.html). 