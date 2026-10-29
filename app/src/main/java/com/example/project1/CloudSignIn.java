package com.example.project1;
import android.content.Intent;
import android.os.CancellationSignal;
import android.widget.Toast;
import androidx.credentials.*;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.*;
public final class CloudSignIn {
    public static void google(BaseActivity activity){
        String client=activity.getString(R.string.default_web_client_id);
        if(client.isEmpty()||com.google.firebase.FirebaseApp.getApps(activity).isEmpty()){Toast.makeText(activity,"Chưa cấu hình Firebase/Google",Toast.LENGTH_LONG).show();return;}
        GetGoogleIdOption option=new GetGoogleIdOption.Builder().setServerClientId(client).setFilterByAuthorizedAccounts(false).build();
        GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity).getCredentialAsync(activity,request,new CancellationSignal(),activity::runOnUiThread,new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
            @Override public void onResult(GetCredentialResponse response){
                try{
                    Credential credential=response.getCredential();
                    if(!(credential instanceof CustomCredential)||!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType()))throw new IllegalArgumentException("Không nhận được tài khoản Google");
                    GoogleIdTokenCredential google=GoogleIdTokenCredential.createFrom(credential.getData());
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(com.google.firebase.auth.GoogleAuthProvider.getCredential(google.getIdToken(),null)).addOnSuccessListener(activity,result->{
                        result.getUser().getIdToken(true).addOnSuccessListener(activity,id->Api.async(activity,()->{new AuthStore(activity).firebase(id.getToken());return true;},ok->{activity.startActivity(new Intent(activity,MainActivity.class));activity.finish();})).addOnFailureListener(activity,e->message(activity,e));
                    }).addOnFailureListener(activity,e->message(activity,e));
                }catch(Exception e){message(activity,e);}
            }
            @Override public void onError(GetCredentialException e){message(activity,e);}
        });
    }
    private static void message(BaseActivity activity,Exception e){Toast.makeText(activity,"Không đăng nhập được Google: "+e.getLocalizedMessage(),Toast.LENGTH_LONG).show();}
}
