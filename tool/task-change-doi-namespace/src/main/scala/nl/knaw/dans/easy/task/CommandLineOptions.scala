package nl.knaw.dans.easy.task

import java.io.{File, PrintWriter}
import java.net.URL

import com.yourmediashelf.fedora.client.request.FedoraRequest
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import nl.knaw.dans.easy.{DataciteService, DataciteServiceConfiguration}
import org.rogach.scallop.ScallopConf

class CommandLineOptions (args: Array[String]) extends ScallopConf(args){

  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))
  banner("""
           Task to correct the DOI namespace in a set of datasets.
           |
           |Options:
           |""".stripMargin)
  val doUpdate = opt[Boolean]("apply-updates", descr = "Without this argument no changes are made to the repository, the default is a test mode that logs the intended changes", default = Some(false))
  val sendEmails = opt[Boolean]("send-emails", descr = "Tells whether emails are sent automatiacally to the depositors", default = Some(false))
  val url = opt[String]("url", noshort = true, descr = "Base url for the fedora repository", default = Some("http://localhost:8080/fedora"))
  val password = opt[String]("password", descr = "Password for fedora repository, if omitted provide it on stdin")
  val username = opt[String]("username", descr = "Username for fedora repository, if omitted provide it on stdin")
  val dcUrl = opt[String]("dc-url", noshort = true, descr = "Base url for the datacite", default = Some("https://datacite.tudelft.nl/dataciteapi/v3/resources?testMode=true"))
  val dcPassword = opt[String]("dc-password", descr = "Password for datacite, if omitted provide it on stdin")
  val dcUsername = opt[String]("dc-username", descr = "Username for datacite, if omitted provide it on stdin")
  val dcResolverUrl =  opt[String]("dc-resolver-url", noshort = true, descr = "Base url for the datacite", default = Some("http://deasy.dans.knaw.nl:8080/ui/datasets/id"))
  val output = opt[String]("output", descr = "Name of the file where the changed pids are written", default = Some("changed_pids.txt"))
  val output_2 = opt[String]("output_2", descr = "Name of the file with information about the changed datasets", default = Some("sent_emails.txt"))
  footer("")
  verify()
}

object CommandLineOptions {
  def parse(args: Array[String]): Settings = {
    val opts = new CommandLineOptions(args)

    val url: URL = new URL(opts.url())
    val username: String = opts.username.get.getOrElse(askUsername(url.toString))
    val password: String = opts.password.get.getOrElse(askPassword(username,url.toString))
    FedoraRequest.setDefaultClient(new FedoraClient(new FedoraCredentials(url, username, password)))

    val dcUrl: URL = new URL(opts.dcUrl())
    val dcUsername: String = opts.dcUsername.get.getOrElse(askUsername(dcUrl.toString))
    val dcPassword: String = opts.dcPassword.get.getOrElse(askPassword(username,dcUrl.toString))
    val dsResolver: URL = new URL(opts.dcResolverUrl())
    val dsConfig : DataciteServiceConfiguration = new DataciteServiceConfiguration()
    dsConfig.setDatasetResolver(dsResolver);
    dsConfig.setUsername(dcUsername);
    dsConfig.setPassword(dcPassword);
    val x = dcUrl.toString
    dsConfig.setDoiRegistrationUri(dcUrl.toString);
    val ds : DataciteService = new DataciteService(dsConfig)

    val testMode = !opts.doUpdate()
    val sendEmails = opts.sendEmails()
    val updater = StreamUpdater.get(testMode)
    val changedPids : String = opts.output()
    val changedDatasets : String = opts.output_2()
    val writer = new PrintWriter(new File(changedPids))
    val writer_2 = new PrintWriter(new File(changedDatasets))

    new Settings(testMode, sendEmails, updater, ds, writer, writer_2) {
      override def toString: String =
        s"Settings: test=$testMode, send-emails=$sendEmails, fedora-user=$username, fedora-baseurl=$url, " +
          s"dc-url=$dcUrl, dc-username=$dcUsername, dc-password=$dcPassword, pids-file=$changedPids, changed files=$changedDatasets"
    }
  }

  def askUsername(url: String): String = {
    print(s"Username for $url: ")
    System.console().readLine()
  }

  def askPassword(user: String, url: String): String = {
    print(s"Password for $user on $url: ")
    System.console.readPassword().mkString
  }
}
