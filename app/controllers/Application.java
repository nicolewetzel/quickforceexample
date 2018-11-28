package controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.index;
import views.html.setup;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class Application extends Controller {
    @Inject
    private Force force;

    private boolean isSetup() {
        try {
            force.consumerKey();
            force.consumerSecret();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String oauthCallbackUrl(Http.Request request) {
        return (request.secure() ? "https" : "http") + "://" + request.host();
    }

    public CompletionStage<Result> index(String code) {
        if (isSetup()) {
            if (code == null) {
                // start oauth
                final String url = "https://login.salesforce.com/services/oauth2/authorize?response_type=code" +
                        "&client_id=" + force.consumerKey() +
                        "&redirect_uri=" + oauthCallbackUrl(request());
                return CompletableFuture.completedFuture(redirect(url));
            } else {
                return force.getToken(code, oauthCallbackUrl(request())).thenCompose(authInfo ->
                        force.getArticles(authInfo).thenApply(Knowledge__kav ->
                                ok(index.render(Knowledge__kav))
                        )
                ).exceptionally(error -> {
                    if (error.getCause() instanceof Force.AuthException)
                        return redirect(routes.Application.index(null));
                    else
                        return internalServerError(" completion stage: " +error.getMessage());
        
                });
                
            }
        } else {
            return CompletableFuture.completedFuture(redirect(routes.Application.setup()));
        }
    }

    public Result setup() {
        if (isSetup()) {
            return redirect(routes.Application.index(null));
        } else {
            final String maybeHerokuAppName = request().host().split(".herokuapp.com")[0].replaceAll(request().host(), "");
            return ok(setup.render(maybeHerokuAppName));
        }
    }


    @Singleton
    public static class Force {
        @Inject
        WSClient ws;

        @Inject
        Config config;

        String consumerKey() {
            return config.getString("consumer.key");
        }

        String consumerSecret() {
            return config.getString("consumer.secret");
        }

        CompletionStage<AuthInfo> getToken(String code, String redirectUrl) {
            final CompletionStage<WSResponse> responsePromise = ws.url("https://login.salesforce.com/services/oauth2/token")
                    .addQueryParameter("grant_type", "authorization_code")
                    .addQueryParameter("code", code)
                    .addQueryParameter("client_id", consumerKey())
                    .addQueryParameter("client_secret", consumerSecret())
                    .addQueryParameter("redirect_uri", redirectUrl)
                    .execute(Http.HttpVerbs.POST);

            return responsePromise.thenCompose(response -> {
                final JsonNode jsonNode = response.asJson();

                if (jsonNode.has("error")) {
                    CompletableFuture<AuthInfo> completableFuture = new CompletableFuture<>();
                    completableFuture.completeExceptionally(new AuthException(jsonNode.get("error").textValue()));
                    return completableFuture;
                } else {
                    return CompletableFuture.completedFuture(Json.fromJson(jsonNode, AuthInfo.class));
                }
            });
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class KnowledgeArticleVersion {
            public String articleNumber;
          //  public String categoryGroups;
           // public String downVoteCount;
           // public String summary;
            public String title;
          //  public String upVoteCount;
           // public String url;
           /// public String viewCount;
            //public String viewScore;



            public String getArticleNumber() {
                return articleNumber;
            }
        
            public void setArticleNumbe(String articleNumber) {
                this.articleNumber = articleNumber;
            }
        }
        

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class QueryResultArticle {
            public List<KnowledgeArticleVersion> articles;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AuthInfo {
            @JsonProperty("access_token")
            public String accessToken;

            @JsonProperty("instance_url")
            public String instanceUrl;
        }

        public static class AuthException extends Exception {
            AuthException(String message) {
                super(message);
            }
        }

        CompletionStage<List<KnowledgeArticleVersion>> getArticles(AuthInfo authInfo) {
            CompletionStage<WSResponse> responsePromise = ws.url(authInfo.instanceUrl + "/services/data/v38.0/support/knowledgeArticles")
                    .addHeader("Authorization", "Bearer " + authInfo.accessToken)
                    .addHeader("Accept-Language", "en-US")
                   // .addQueryParameter("q", "SELECT title, summary FROM KnowledgeArticleVersion ")
                    .get();
                    System.out.println("queryran" + responsePromise); 
                    

            return responsePromise.thenCompose(response -> {
                final JsonNode jsonNode = response.asJson();
                //System.out.println("JSON RESPONSE" + response.asJson()); 
                System.out.println("JSON NODE" + jsonNode);
                if (jsonNode.has("error")) {
                    CompletableFuture<List<KnowledgeArticleVersion>> completableFuture = new CompletableFuture<>();
                    completableFuture.completeExceptionally(new AuthException(jsonNode.get("error").textValue()));
                    return completableFuture; 
                } else {
                   
                    QueryResultArticle QueryResultArticle = Json.fromJson(jsonNode, QueryResultArticle.class);
                    System.out.println("QueryResultArticle.class " + QueryResultArticle.class); 
             
                    System.out.println("QueryResultArticle.record NEW TEST " + CompletableFuture.completedFuture(QueryResultArticle.articles));
                    System.out.println("QueryResultArticle.record NEW TEST " + QueryResultArticle.articles);
                    return CompletableFuture.completedFuture(QueryResultArticle.articles);
                
                }
               
            }
            );
        }
    }

}


