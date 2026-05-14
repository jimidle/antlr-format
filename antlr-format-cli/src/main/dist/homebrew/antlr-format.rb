class AntlrFormat < Formula
  desc "Format ANTLR grammar files"
  homepage "https://github.com/jimidle/antlr-format"
  url "__ARCHIVE_URL__"
  sha256 "__ARCHIVE_SHA256__"
  license "Apache-2.0"
  depends_on "openjdk@21"

  def install
    libexec.install "bin", "lib"
    bash_completion.install "completions/antlr-format.bash" => "antlr-format"
    zsh_completion.install "completions/_antlr-format"
    fish_completion.install "completions/antlr-format.fish"
    (bin/"antlr-format").write_env_script libexec/"bin/antlr-format", Language::Java.overridable_java_home_env
  end

  test do
    (testpath/"Demo.g4").write <<~EOS
      grammar Demo;
      a:'a';
    EOS

    output = shell_output("#{bin}/antlr-format #{testpath}/Demo.g4")
    assert_match "grammar Demo;", output
  end
end
