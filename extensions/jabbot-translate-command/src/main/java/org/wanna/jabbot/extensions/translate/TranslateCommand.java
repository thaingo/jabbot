package org.wanna.jabbot.extensions.translate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wanna.jabbot.command.AbstractCommandAdapter;
import org.wanna.jabbot.command.CommandMessage;
import org.wanna.jabbot.command.DefaultCommandMessage;
import org.wanna.jabbot.command.config.CommandConfig;
import org.wanna.jabbot.extensions.translate.binding.Result;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author tsearle <tsearle>
 * @since 2015-03-21
 */
public class TranslateCommand extends AbstractCommandAdapter {
	final Logger logger = LoggerFactory.getLogger(TranslateCommand.class);
	final ObjectMapper mapper = new ObjectMapper();

	public TranslateCommand(CommandConfig configuration) {
		super(configuration);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public String getHelpMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(getCommandName());
		sb.append(" <src_lang> <dst_lang> message\n");
		sb.append("Returns message translated from src_lang to dst_lang\n");
		sb.append("src_lang and dst_lang are 2 digit codes\n");
		return sb.toString();
	}

	@Override
	public DefaultCommandMessage process(CommandMessage message) {
		List<String> args = getArgsParser().parse(message.getBody());
		String options = null;
		if(args != null && args.size() >= 3){
			try {
				options = "langpair="+ URLEncoder.encode(args.get(0)+ "|"+ args.get(1),"UTF-8");
				options+="&q="+ URLEncoder.encode(StringUtils.join(args.subList(2,args.size()), " "),"UTF-8");
			} catch (UnsupportedEncodingException e) {
				logger.error("An error occured while encoding param {}",message.getBody(),e);
			}
			try {
				String response = query(options);
				Result parsed = mapper.readValue(response,Result.class);
				if(parsed.getResponseData() != null){
					String translation = StringEscapeUtils.unescapeHtml4(parsed.getResponseData().getTranslatedText());
					DefaultCommandMessage result = new DefaultCommandMessage();
					result.setBody(translation);
					return result;
				}
			} catch (IOException e) {
				logger.error("error querying translation service",e);
			}
		}

		DefaultCommandMessage result = new DefaultCommandMessage();
		result.setBody("Insufficent Arguments: <src_lang> <dst_lang> <message>");
		return result;
	}

	/**
	 * Make sure one does not use / command from jabber in order to spam someone else
	 * using /say or having the bot acting weird using /me.
	 * by Stripping all the leading / from the response
	 *
	 * @param response the raw response to be returned
	 * @return cleaned response
	 */
	private String secureResponse(String response) throws UnsupportedEncodingException {
		logger.debug("securing response {}",response);
		response = URLDecoder.decode(response, "UTF-8");
		while(response.startsWith("/")){
			response = response.replace("/","");
		}
		return response;
	}

	private String query(String option) throws IOException {
		final DefaultHttpClient httpclient = new DefaultHttpClient();
		String url = "http://api.mymemory.translated.net/get?";
		if(option != null ){
			url += option;
		}
		logger.debug("querying {}",url);
		HttpGet httpGet = new HttpGet(url);
		httpGet.setHeader("Accept","text/plain");

		try
		{
			HttpResponse response = httpclient.execute(httpGet);
			HttpEntity entity = response.getEntity();
			if (entity != null)
			{
				return EntityUtils.toString(entity, HTTP.UTF_8);
			}
		} catch (IOException e) {
			logger.error("error querying translate",e);
		}

		return null;
	}
}