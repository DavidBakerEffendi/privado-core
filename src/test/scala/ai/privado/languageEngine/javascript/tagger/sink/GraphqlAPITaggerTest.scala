package ai.privado.languageEngine.javascript.tagger.sink

import ai.privado.cache.{RuleCache, TaggerCache}
import ai.privado.languageEngine.javascript.tagger.sink.GraphqlAPITagger
import ai.privado.languageEngine.javascript.tagger.source.IdentifierTagger
import ai.privado.model.*
import better.files.File
import io.joern.jssrc2cpg.{Config, JsSrc2Cpg}

import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.semanticcpg.language.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class GraphqlAPITaggerTest extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private val cpgs        = mutable.ArrayBuffer.empty[Cpg]
  private val outPutFiles = mutable.ArrayBuffer.empty[File]
  private val inputDirs   = mutable.ArrayBuffer.empty[File]
  val ruleCache           = new RuleCache()

  val sourceRule = List(
    RuleInfo(
      "Data.Sensitive.FirstName",
      "FirstName",
      "",
      Array(),
      List("(?i).*firstName.*"),
      false,
      "",
      Map(),
      NodeType.REGULAR,
      "",
      CatLevelOne.SOURCES,
      "",
      Language.JAVASCRIPT,
      Array()
    )
  )

  val sinkRule = List(
    RuleInfo(
      Constants.thirdPartiesAPIRuleId,
      "Third Party API",
      "",
      Array(),
      List("post|get|all|delete|put|patch|head|subscribe|unsubscribe"),
      false,
      "",
      Map(),
      NodeType.API,
      "",
      CatLevelOne.SINKS,
      catLevelTwo = Constants.third_parties,
      Language.JAVASCRIPT,
      Array()
    )
  )

  val systemConfig = List(
    SystemConfig(
      "apiGraphqlLibraries",
      "(?i)(.*ApolloClient|graphql|@apollo\\\\/client|express-graphql|nexus|apollo-server-.*|apollo-link-.*|google-graphql-functions|@join-com\\\\/gcloud-apollo-express-logger|autographql|modern-project-generator|@greguintow\\\\/apollo-server-cloud-functions|@keplr\\\\/graphql-changes-slack-notifier|@gapi\\\\/sendgrid|apollo-server|@octokit\\\\/graphql|@graphql-codegen\\\\/core|@graphql-tools\\\\/relay-operation-optimizer|react-apollo|relay-hooks|@apollo\\\\/react-components|@apollo\\\\/react-ssr|next-apollo|gatsby-source-graphcms|mongoose-to-graphql|@gql2ts\\\\/util|apollo-angular-link-http-common|relay-decorator|core-types|prisma1|@n1ru4l\\\\/in-memory-live-query-store|@times-components\\\\/utils|@nestjs\\\\/graphql|@nestjs-query\\\\/query-graphql|@divyenduz\\\\/graphql-language-service-parser|@accounts\\\\/server|get-countries-info|@kamilkisiela\\\\/graphql-tools|gatsby-source-graphql-universal|graphiql-code-exporter|gatsby-graphql-source-toolkit|loopback-graphql-server|nothinkdb|gatsby-plugin-graphql-codegen|vtex-graphql-builder|@gql2ts\\\\/from-query|primus-graphql|generator-es6-graphql|rnrf-relay-renderer|gql-error|fetch-dedupe|@times-components\\\\/provider-queries|moleculer-apollo-server|ts-transform-graphql-tag|svelte-apollo|nuxt-graphql-request|@giraphql\\\\/core|gql-query-builder|ibm-graphql-query-generator|@apollographql\\\\/apollo-upload-server|@typerpc\\\\/plugin-utils|@typerpc\\\\/plugin|@apollo\\\\/federation-internals|gatsby-source-prismic-graphql|@jesstelford\\\\/apollo-cache-invalidation|superagent-graphql|@cdmbase\\\\/graphql-type-uri|@gramps\\\\/gramps|@giraphql\\\\/plugin-scope-auth|@giraphql\\\\/plugin-validation|@focus-reactive\\\\/storybook-graphql-kit|@giraphql\\\\/plugin-relay|datasource-sql|@giraphql\\\\/plugin-prisma|join-monster-graphql-tools-adapter|houdini|@ssb-graphql\\\\/main|apollo-logger|@apollo-elements\\\\/interfaces|bs-graphql|@ethql\\\\/base|gql-now|async-cache-dedupe|@pickk\\\\/common|tt-model|@flopflip\\\\/http-adapter|@entria\\\\/graphql-mongoose-loader|mobx-graphlink|@rxdi\\\\/graphql|egg-graphql|@hasura-ws\\\\/core|apollo-datasource-http|@theguild\\\\/graphql-language-service-types|@golevelup\\\\/nestjs-graphql-request|@dotvirus\\\\/yxc|altair-graphql-plugin|@browserql\\\\/fragments|craco-graphql-loader|@theguild\\\\/graphql-language-service-utils|@loona\\\\/core|@brownpapertickets\\\\/surya-gql-data|@tegh\\\\/core|@tira\\\\/tira-errors|@rxdi\\\\/hapi|@ssb-graphql\\\\/tribes|relay-enum-generator|surya-gql-data|@apollo-model\\\\/graphql-tools|gh-gql|@theguild\\\\/graphql-language-service|@gramps\\\\/errors|altair-fastify-plugin|@bloomreach\\\\/graphql-commerce-connector-service|@absinthe\\\\/socket-relay|@brownpapertickets\\\\/surya-gql-types|@fjedi\\\\/graphql-shield|objection-graphql|@forrestjs\\\\/service-fastify-gql|@graphql-portal\\\\/dashboard|@forrestjs\\\\/service-apollo|@r26d\\\\/absinthe-apollo-socket|@gapi\\\\/ipfs|ra-data-graphql-simple|nest-graphql-utils|@thiscover\\\\/discover|super-graphiql-express|@joystream\\\\/warthog|@aerogear\\\\/graphql-query-mapper|ssb-helpers|mercurius-upload|@rxdi\\\\/compressor|openapi-graphql|graphene-js|@activimetrics\\\\/socket|gql-generator-node|bs-apollo-server-express|@browserql\\\\/cache|github-graphql-api|format-graphql|gatsby-plugin-sanity-image|relay-link-http-common|@subhuti\\\\/type|@ethql\\\\/core|ra-data-opencrud|@gapi\\\\/ipfs-daemon|@rxdi\\\\/graphql-rabbitmq-subscriptions|graphml-parser|@gql2ts\\\\/language-flow|gatsby-plugin-apollo-shopify|nestjs-dataloader|@foo-software\\\\/ghost-graphql|mst-gql|easygraphql-parser-gamechanger|fib-graphql|@entria\\\\/graphql-mongo-helpers|express-graphiql|hapi-plugin-graphiql|batched-graphql-request|surya-gql-types|js-core-data-graphql|@lifeomic\\\\/graphql-resolvers-xray-tracing|hapi-graphql|@magnus-plugins\\\\/apollo-server-fastify|@opencreek\\\\/neo4j-graphql|apollo-cache-|gql2flow|@prisma-cms\\\\/query-builder|@neo4j\\\\/graphql-ogm|@typerpc\\\\/go-plugin-utils|@oudy\\\\/graphql|@creditkarma\\\\/graphql-loader|@valueflows\\\\/vf-graphql|relay-nextjs|graysql|@browserql\\\\/firestore|flow-dynamic|@hauxir\\\\/absinthe-socket|@rqsts\\\\/react-data-graphql|@apollo-orbit\\\\/core|@rxdi\\\\/rabbitmq-pubsub|@theguild\\\\/graphql-language-service-parser|@bilgorajskim\\\\/ra-data-graphql|@dracul\\\\/customize-backend|@landingexp\\\\/apollo-reporting-protobuf|@theguild\\\\/graphql-language-service-interface|vn-kooch-data-graphql|@enigmatis\\\\/polaris-typeorm|@yeutech\\\\/ra-data-graphql|mongease-graphql|trepo-core|@browserql\\\\/react|@blueeast\\\\/graphql-mqtt-subscriptions|@dillonkearns\\\\/elm-graphql|@n1ru4l\\\\/socket-io-graphql-server|bs-promise-router|rest-graphql|@devinit\\\\/datahub-api|codegen-prismic-fetch|@cortexql\\\\/ts2graphql|@crossroad\\\\/manager|@brownpapertickets\\\\/surya-gql-auth|@graphql-guru\\\\/loader|@dwwoelfel\\\\/graphql-parse-resolve-info|@nestbox\\\\/core|ts2graphql|@subhuti\\\\/core|gatsby-plugin-graphql-component|@graphql-portal\\\\/datasources|openapi2graph|@emrys-myrddin\\\\/ra-data-graphql|@golevelup\\\\/nestjs-hasura|subscriptions-transport-sse|@openreplay\\\\/tracker-graphql|@hasura-ws\\\\/prepare|@enigmatis\\\\/polaris-middlewares|nestjs-graphql-dataloader|sails-graphql-adapter|@hasura-ws\\\\/model|@hydre\\\\/graphql-batch-executor|@corejam\\\\/plugin-auth|@o\\\\/swagger-to-graphql|yonderbox-graphql-mongodb-adapter|@scalars\\\\/grapi|@iteria-app\\\\/graphql-lowcode|@limit0\\\\/mongoose-graphql-pagination|@limit0\\\\/graphql-custom-types|@cortical\\\\/ts2graphql|@ablestack\\\\/rdo|@wyze\\\\/gatsby-source-graphql|@gramps\\\\/gramps-express|@ci-custom-module\\\\/api|@mitoai\\\\/gintonic|gatsby-source-mercadolibre|@apollo-waterline\\\\/policies|fastify-apollo-step|simplistik|@apollo-waterline\\\\/errors|@girin\\\\/auth|@graphql-reshape\\\\/transformers|annotated-graphql|@axelspringer\\\\/graphql-google-pubsub|@brownpapertickets\\\\/surya-gql-data-mongodb|@whatsgood\\\\/uniform-graphql|@pyramation\\\\/postgis|@type-properties\\\\/identifier|@dotansimha\\\\/openapi-to-graphql|@type-properties\\\\/encryption|sequelize-graphql-tools|nextql|@curlybrace\\\\/auth|@apollographql\\\\/graphql-language-service-server|@landingexp\\\\/apollo-server-express|@jovercao\\\\/graphql.js|git-get-repos-labels|@curlybrace\\\\/mediaservice|wasp-graphql|@swatikumar\\\\/openapi-to-graphql|@timkendall\\\\/tql|apollo-gateway-tracing|isotropy-graphql|dat-graphql|validate-graphql-page-args|apollo-server-core-tracing|graphile-search-plugin|easygraphql-format-error|@theguild\\\\/graphiql-toolkit|relay-sentry|git-del-repos-labels|@fevo-tech\\\\/graphql-codegen-core|@prisma-cms\\\\/connector|graphile-simple-inflector|@sayjava\\\\/scaffold-csv-source|@wildcards\\\\/reason-apollo|git-update-repos-labels|@sayjava\\\\/scaffold-json-source|graysql-orm-loader|zorgs|@browserql\\\\/inputs|@jovercao\\\\/gql-js|react-apollo-graphqls|@theguild\\\\/graphql-language-service-server|react-relay-offline|github-profile-status|@superalgos\\\\/web-components|altair-express-middleware|apollo-datasource-graphql|oasgraph-jibz|@ethql\\\\/ens|cursor-connection|@z4o4z\\\\/gatsby-source-graphql-universal|artemis-utilities|anagraphql|relay-link-batch|mongoose-plugin-dataloader|wonder-bs-graphql|@prismicio\\\\/gatsby-source-prismic-graphql|@unitz\\\\/gqlbuilder|apollo-error-overlay|@ethql\\\\/erc20|@crossroad\\\\/registry|@crossroad\\\\/rules|@kanmii\\\\/socket|create-graphql-server-logging|@h1u2i3\\\\/socket|@phony\\\\/server|create-graphql-server-find-by-ids|traverse-apollo-server-core|@prisma-cms\\\\/yley|@phony\\\\/utils|quervana|@subhuti\\\\/sequelize|@yamadayuki\\\\/bs-graphql|preact-apollo-fix|buildkite-query|webql-codegen-core|@expo\\\\/graphql-server-core|persimon|@the-gear\\\\/graphql-rewrite|graph-data-layer|afrik-server-module-graphiql|openapi-to-graphql-pwr|fetch-github-graphql|graysql-orm-loader-waterline|smartapi-oasgraph|apollo-hooks-extended|postgraphile-apollo-server|@looop\\\\/graphql-depth-limit|postgraphile-remove-foreign-key-fields-plugin|@n1ru4l\\\\/graphql-live-query-patch-jsondiffpatch|gqlmin|@graphile\\\\/persisted-operations|@urql\\\\/rescript|nest-graphql-scalar-adapter|@tomekf\\\\/gqlite|@bufferapp\\\\/bufftracer|@apollo\\\\/graphql|gatsby-plugin-graphql-config|prisma-graphql-type-decimal|apollo-angular-link-persisted|hops-msw|@lenne.tech\\\\/nest-server|uniforms-bridge-graphql|gqtx|react-apollo-fragments|shopify-graphql-node|typed-graphql-subscriptions|vscode-artemis-relay|vite-plugin-graphql|@apollo\\\\/query-graphs|@jcm\\\\/nexus-plugin-relay-global-id|@apollo\\\\/composition|gql-hook-codegen|slonik-dataloaders|react-apollo-decorators|@wepublish\\\\/api|mercurius-apollo-registry|apollo-datasource-soap|@cartons\\\\/apollo-upload|@capaj\\\\/graphql-depth-limit|@ctx-core\\\\/iex-graphql|@herbsjs\\\\/herbs2gql|apollo-angular-link-http-batch|prisma-typegraphql-types-generator|helix-flare|@entria\\\\/relay-utils|openapi-to-graphql-nullable|postgraphile-plugin-atomic-mutations|@ogma\\\\/platform-graphql-fastify|ra-postgraphile|@moogs\\\\/query-graphql|httpyac|gql-to-ts|glimmer-apollo|@crawlo\\\\/graphql|omerman-|gql-dedup|mercurius-cache|@squareark\\\\/sdk|@giraphql\\\\/plugin-errors|dataloader-values|@graphql-codegen\\\\/relay-operation-optimizer|@gapi\\\\/apache-kafka|instagram-graph-sdk|@gapi\\\\/core|@giraphql\\\\/plugin-dataloader|next-graphql-react|koa-shopify-graphql-proxy-cookieless|apollo-remove-typename-mutation-link|@gapi\\\\/sequelize|inversify-graphql|@webundsoehne\\\\/nestjs-graphql-typeorm-dataloader|apollo-mongoose-plugin|typegraphql-prisma-muhad|@correttojs\\\\/next-utils|gatsby-plugin-playground|@dracul\\\\/queue-backend|@gapi\\\\/microservices|apollo-datasource-cosmosdb|@reform\\\\/bundle-sass|@n1ru4l\\\\/graphql-live-query-patch-json-patch|nest-graphql-endpoint|@reform\\\\/bundle-graphql|nestjs-graphql-resolver|@gapi\\\\/onesignal-notifications|@gapi\\\\/voyager|@skyra\\\\/star-wars-api|@reform\\\\/bundle-html-entry|@ogma\\\\/platform-graphql|@n1ru4l\\\\/graphql-codegen-relay-optimizer-plugin|@codification\\\\/cutwater-graphql|@giraphql\\\\/plugin-smart-subscriptions|apollo-graph-definition-generator|koa-graphiql|codegen-typescript-graphql-module-declarations-plugin|@theydo\\\\/graphql-directive-requires-authentication|@giraphql\\\\/plugin-example|apollo-graphql-ws-link|@gapi\\\\/playground|gatsby-plugin-altair-graphql|require-graphql-file|fastify-gql-upload|@gapi\\\\/auth|@coderich\\\\/autograph|github-openapi-graphql-query|apollo-reporting-protobuf|@apollo\\\\/federation|vue-apollo|type-graphql|@apollo\\\\/gateway|@n1ru4l\\\\/graphql-live-query|@apollo\\\\/react-hooks|apollo-errors|@apollographql\\\\/graphql-upload-8-fork|@apollographql\\\\/graphql-language-service-|@apollo\\\\/query-planner|koa-graphql|@apollo\\\\/react-common|ts-graphql-plugin|@aws-amplify\\\\/graphql-.*|@graphiql\\\\/toolkit|@apollo\\\\/react-hoc|apollo-engine-reporting-protobuf|swagger-to-graphql|graphile-utils|@harmonyjs\\\\/controller-apollo|apollo-resolvers|@apollo\\\\/subgraph|highlightjs-graphql|apollo-angular-link-http|graph.ql|relay-compiler-language-typescript|@divyenduz\\\\/graphql-language-service-types|gotql|fastify-apollo|sails-graphql|loopback-graphql|easygraphql-parser|ra-data-graphql|@rollup\\\\/plugin-graphql|@apollo\\\\/rover|cf-graphql|@envelop\\\\/core|openapi-to-graphql|@divyenduz\\\\/graphql-language-service|granate|http-link-dataloader|universal-hot-reload|waterline-graphql|gql-generator|@kbrandwijk\\\\/swagger-to-graphql|typegraphql-prisma|meteor-apollo-accounts|@absinthe\\\\/socket-apollo-link|waterline-to-graphql|sequelize-relay|graphiql-ui|adonis-apollo-server|@graphql-toolkit\\\\/relay-operation-optimizer|altair-graphql-core|gqlx|apollo-server-restify|loopback-graphql-relay|jsontographql|@mathix420\\\\/graphql|stateslang|generator-nodejs-api|react-blips|spikenail|@gql2ts\\\\/types|ej2-graphql-adaptor|altair-static|next-apollo-provider|core-types-graphql|@raynode\\\\/graphql-anywhere|coffee-relay|lambda-graphql|react-fgql|graph-entity|apollo-paean-wordpress|graph-quill|@exogen\\\\/graphql-tools|relay-fullstack|express-graph.ql|sofa-api|sparqljson-to-tree|@neo4j\\\\/graphql|type-graphql-dataloader|@ardatan\\\\/graphql-tools|soap-graphql|@nestlab\\\\/google-recaptcha|shopify-gid|@browserql\\\\/fpql|mercurius-codegen|apollo-type-bigint|rivet-graphql|@n1ru4l\\\\/graphql-live-query-patch|apollo-angular-boost|@yonderbox\\\\/graphql-colors|relay-link|nanographql|@grapi\\\\/server|typegraphql-nestjs|apollo-datasource-dynamodb|ply-ct|@hoangvvo\\\\/graphql-jit|@palpinter\\\\/moleculer-apollo-server|react-apollo-graphql|falcor-graph-syntax|@yonderbox\\\\/graphql-mongodb-adapter|gatsby-plugin-apollo|@graphql-sse\\\\/server|gatsby-plugin-graphql-loader|rescript-relay|@advancedalgos\\\\/web-components|cat-graphql|objection-graphql-relay|@graphity\\\\/types|graphity|cloud-graphql|@yonderbox\\\\/graphql-adapter|@svelkit\\\\/graphql|@tira\\\\/tira-project-template|neo4j-graphql-binding|@kohanajs\\\\/graphql-to-orm|rip-hunter|@alpine-code\\\\/node-red-contrib-graphql|@focus-reactive\\\\/storybook-addon-graphcms|apollo-datasource-firestore|type-graph-orm|@ssb-graphql\\\\/stats|@browserql\\\\/operations|@graphql-workspaces\\\\/load|@puti94\\\\/gql-utils|@ssb-graphql\\\\/pataka|hera-js|onepiece-federation|cl-graphql-language-service-interface|relay-sequelize|@creatiwity\\\\/ra-data-graphql|@nlabs\\\\/rip-hunter|express-graphiql-toolbox|@browserql\\\\/contracts|jason-graphql-server|relay-common|relay-mongoose-connection|kendryte|subkit-graphiql|gql-fetch|@mochilabs\\\\/ra-data-graphql|@zuu\\\\/owl|yonderbox-graphql-colors|isotropy-plugin-graphql|@jumpn\\\\/absinthe-phoenix-socket|proptypes-parser|@brownpapertickets\\\\/surya-gql-data-pgsql|@goldix.org\\\\/graphql|@greenwood\\\\/plugin-graphql|@fjedi\\\\/graphql-api|@apollographql\\\\/graphql-playground-middleware-lambda|gverse|validation-error-gql|@txstate-mws\\\\/graphql-server|ra-data-hasura-graphql|@mzronek\\\\/openapi-to-graphql|@cobraz\\\\/nestjs-dataloader|fastify-gql-upload-ts|@giraphql\\\\/plugin-sub-graph|@foal\\\\/graphql|@giraphql\\\\/converter|@logilab\\\\/gatsby-plugin-elasticsearch|@vuex-orm\\\\/plugin-graphql|@saeris\\\\/apollo-server-vercel|@apollo-elements\\\\/rollup-plugin-graphql|gql-logger|skyhook-graphql-sdk|@kazekyo\\\\/nau|altair-koa-middleware|grandstack|relay-nextjs-next|@sokolabs\\\\/graphql-fields-list|@favware\\\\/graphql-pokemon|@pipedrive\\\\/graphql-query-cost|@rxdi\\\\/credit-card-form|@grafoo\\\\/core|gatsby-plugin-json-pages|@dblechoc\\\\/vite-plugin-relay|@saeris\\\\/graphql-scalars|@kibeo\\\\/mk-gql|json-logic-js-graphql|evolutility-ui-react|dataloaderx|core-mvc|@grafoo\\\\/react|@saipyenepalli\\\\/graphql-to-mongodb-spy|@gapi\\\\/amqp|query-builder-graphql|@merged\\\\/solid-apollo|@envelop\\\\/statsd|@rxdi\\\\/ipfs-package-example|gatsby-plugin-algolia-search|easy-dgraph|@wiicamp\\\\/graphql-merge-resolvers|@fjedi\\\\/graphql-react-components|gqlite-lib|apollo-hooks-codegen|@absinthe\\\\/socket-graphiql|@graphile\\\\/postgis|millan|moesif-nodejs|@prismicio\\\\/gatsby-source-graphql-universal|reason-apollo|@giraphql\\\\/plugin-simple-objects|gql-compress|@loopback\\\\/graphql|rollup-plugin-graphql|@landingexp\\\\/apollo-server-core|graphi|gql2ts|vscode-apollo-relay|@curlybrace\\\\/framework|@ishop\\\\/core|@apollo-elements\\\\/lib|apicalypse|@graphql-authz\\\\/core|@grafoo\\\\/bindings|@giraphql\\\\/plugin-directives|@enigmatis\\\\/polaris-common|@tira\\\\/tira-graphql|@anthor\\\\/graphql-compose-mongoose|hops-react-apollo|@forrestjs\\\\/service-express-graphql|lunar-core|@girin\\\\/framework|igroot-fetch|@ssb-graphql\\\\/invite|prisma-nestjs-graphql|@jamo\\\\/graphql-request|apollo-log|@saeris\\\\/graphql-directives|@digest\\\\/graphql|@tsed\\\\/graphql|@udia\\\\/graphql-postgres-subscriptions|@brownpapertickets\\\\/surya-gql-scalar|@rxdi\\\\/graphql-pubsub|@molaux\\\\/mui-crudf|type-graphql.macro|react-apollo-mutation-state|@pandaai\\\\/graphql-fork|reason-graphql|apollo-progressive-fragment-matcher|@est-normalis\\\\/simple-apollo-logger|mercurius-apollo-tracing|@cartons\\\\/graphql-upload|@serafin\\\\/api|@zaibot\\\\/graphql-cosmos|apollo-datasource-firebase|simplify-graphql|type-graphql-dataloader-integrated|@nebo.digital\\\\/query-graphql|@apollo\\\\/link-ws|apollo-tracing|@solui\\\\/graphql).*",
      Language.JAVASCRIPT,
      "",
      Array()
    ),
    SystemConfig(
      "apiGraphqlReadSink",
      "(?i)(fetchapi|fetchlegacyxml|get|getInputStream|getApod|getForObject|getForEntity|list|proceed|trace|Path|getInput|getOutput|getResponse|marshall|unmarshall|on|url|client|openConnection|request|execute|newCall|load|host|access|usequery|fetch|useSubscription|useFragment|usePaginationFragment|asyncIterator|graphqlExpress)",
      Language.JAVASCRIPT,
      "",
      Array()
    ),
    SystemConfig(
      "apiGraphqlWriteSink",
      "(?i)(createfetch|postform|axios|cors|set|post|put|patch|Path|send|sendAsync|remove|delete|write|read|assignment|provider|exchange|postForEntity|call|createCall|createEndpoint|dispatch|invoke|newMessage|asyncSend|emit|makeExecutableSchema|gql|commitMutation|useMutation|publish|joinMonsterAdapt)",
      Language.JAVASCRIPT,
      "",
      Array()
    )
  )

  "Graphql READ Example" should {
    val cpg = graphqlCode("""
                            |const { graphql, buildSchema } = require('graphql');
                            |
                            |// Builds a schema using the GraphQL schema language
                            |const schema = buildSchema(`
                            |  type Query {
                            |    hello: String
                            |  }
                            |`);
                            |
                            |// The root of our graph gives us access to resolvers for each type and field
                            |const resolversRoot = {
                            |  hello: () => {
                            |    return 'Hello world!';
                            |  },
                            |};
                            |
                            |// Run a simple graphql query '{ hello }' and then print the response
                            |graphql.load(schema, '{ hello }', resolversRoot).then((response) => {
                            |  console.log(JSON.stringify(response.data));
                            |});
                            |""".stripMargin)
    "Sink should be tagged with API (READ)" in {
      val callNode = cpg.call.methodFullName("graphql.load").head
      callNode.name shouldBe "load"
      callNode.tag.size shouldBe 6
      callNode.tag
        .nameExact(Constants.id)
        .head
        .value shouldBe (Constants.thirdPartiesAPIRuleId + Constants.READ_WITH_BRACKETS)
      callNode.tag.nameExact(Constants.catLevelOne).head.value shouldBe Constants.sinks
      callNode.tag.nameExact(Constants.catLevelTwo).head.value shouldBe Constants.third_parties
      callNode.tag.nameExact(Constants.nodeType).head.value shouldBe "api"
      callNode.tag
        .nameExact("third_partiesapi")
        .head
        .value shouldBe (Constants.thirdPartiesAPIRuleId + Constants.READ_WITH_BRACKETS)
      callNode.tag
        .nameExact("apiUrlSinks.ThirdParties.API (Read)")
        .head
        .value shouldBe (Constants.API + Constants.READ_WITH_BRACKETS)
    }
  }

  def graphqlCode(code: String): Cpg = {
    val inputDir = File.newTemporaryDirectory()
    inputDirs.addOne(inputDir)
    (inputDir / "graphql-example.js").write(code)
    val outputFile = File.newTemporaryFile()
    outPutFiles.addOne(outputFile)
    val rule: ConfigAndRules =
      ConfigAndRules(sourceRule, sinkRule, List(), List(), List(), List(), List(), List(), systemConfig, List())
    val ruleCache = new RuleCache()
    ruleCache.setRule(rule)
    val config      = Config().withInputPath(inputDir.toString()).withOutputPath(outputFile.toString())
    val cpg         = new JsSrc2Cpg().createCpgWithAllOverlays(config).get
    val taggerCache = new TaggerCache()
    new IdentifierTagger(cpg, ruleCache, taggerCache).createAndApply()
    new GraphqlAPITagger(cpg, ruleCache).createAndApply()
    cpgs.addOne(cpg)
    cpg
  }
}
