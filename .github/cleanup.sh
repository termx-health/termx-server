#!/bin/bash

# Set up variables
GITHUB_TOKEN=$1
GITHUB_REPOSITORY_OWNER=$2
PACKAGE_NAME=$3

# Make API Request
response=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
"https://api.github.com/orgs/$GITHUB_REPOSITORY_OWNER/packages/container/$PACKAGE_NAME/versions")

# Process the response
for version in $(echo "$response" | jq -r '.[] | @base64'); do
  _jq() {
    echo ${version} | base64 --decode | jq -r ${1}
  }
  version_id=$(_jq '.id')
  echo "$version_id"
  tags=$(_jq '.metadata.container.tags')
  echo "$tags"
  downloads=$(_jq '.metadata.container.downloads')

  # Check if tags is null or empty and set tags_length accordingly
  if [ -z "$tags" ] || [ "$tags" == "null" ]; then
    tags_length=0
  else
    tags_length=$(echo "$tags" | jq 'length')
  fi

  # Check if downloads is null or empty and set downloads_value accordingly
  if [ -z "$downloads" ] || [ "$downloads" == "null" ]; then
    downloads_count=0
  else
    downloads_count=$downloads
  fi  

  if [ "$downloads_count" -eq 0 ] && [ "$tags_length" -eq 0 ]; then
    echo "Removing unused and untagged version: $version_id"
    curl -X DELETE -H "Authorization: token $GITHUB_TOKEN" \
      "https://api.github.com/orgs/$GITHUB_REPOSITORY_OWNER/packages/container/$PACKAGE_NAME/versions/$version_id"
  fi
done

