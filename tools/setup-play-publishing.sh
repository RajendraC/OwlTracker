#!/usr/bin/env bash
# One-time setup so the "Release bundle for Google Play" workflow can send builds to Google Play's
# testing tracks by itself. Run it in Google Cloud Shell (console.cloud.google.com, the >_ button at
# the top right), signed in with the Google account that owns the Play Console developer account:
#
#   bash <(curl -sL https://raw.githubusercontent.com/RajendraC/OwlTracker/main/tools/setup-play-publishing.sh)
#
# What it makes, all free:
#   - a Google Cloud project "Owl Tracker publishing" with the Google Play Developer API switched on
#   - a service account "play-publisher" that has no Google Cloud permissions at all
#   - a Workload Identity Federation pool that trusts GitHub's sign-in tokens only for the
#     RajendraC/OwlTracker repository's main branch, so no key or password is ever stored anywhere
# Afterwards: invite the printed service account in Play Console (Release apps to testing tracks,
# Owl Tracker only) and add the two printed values as GitHub repository variables.
# To undo: delete the project (gcloud projects delete PROJECT_ID) and remove the user in Play Console.
set -euo pipefail

REPO="RajendraC/OwlTracker"
PROJECT_ID="${PROJECT_ID:-owl-tracker-play-$(date +%s | tail -c 7)}"
POOL="github"
PROVIDER="owltracker"
SA_NAME="play-publisher"

# Google takes a few seconds to make new projects, APIs and accounts usable; retry until it has.
retry() {
  local i
  for i in 1 2 3 4 5 6; do
    "$@" && return 0
    echo "  (not ready yet, trying again in 10 s)"
    sleep 10
  done
  "$@"
}

echo "Creating Google Cloud project $PROJECT_ID ..."
gcloud projects create "$PROJECT_ID" --name="Owl Tracker publishing" --quiet
gcloud config set project "$PROJECT_ID" --quiet
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')

echo "Switching on the APIs (Play Developer, IAM credentials, security token) ..."
retry gcloud services enable androidpublisher.googleapis.com iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com --quiet

echo "Creating the publishing account ..."
retry gcloud iam service-accounts create "$SA_NAME" --display-name="Owl Tracker Play publisher (GitHub Actions)" --quiet
SA="$SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"

echo "Trusting GitHub Actions for $REPO (main branch only) ..."
retry gcloud iam workload-identity-pools create "$POOL" --location=global --display-name="GitHub Actions" --quiet
retry gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" \
  --location=global --workload-identity-pool="$POOL" --display-name="OwlTracker repository" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository == '$REPO' && assertion.ref == 'refs/heads/main'" --quiet
retry gcloud iam service-accounts add-iam-policy-binding "$SA" --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$POOL/attribute.repository/$REPO" \
  --quiet > /dev/null

cat <<DONE

Done. Two more steps, both by hand:

1. Play Console -> Users and permissions -> Invite new users
   Email address:   $SA
   App permissions: Add app -> Owl Tracker -> tick only "Release apps to testing tracks"
   (leave every account permission unticked), then Invite user.

2. GitHub -> RajendraC/OwlTracker -> Settings -> Secrets and variables -> Actions -> Variables tab
   -> New repository variable, twice:
   PLAY_WIF_PROVIDER    = projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$POOL/providers/$PROVIDER
   PLAY_SERVICE_ACCOUNT = $SA

These two values are addresses, not passwords: they only work for GitHub Actions runs from the
OwlTracker repository's main branch.
DONE
